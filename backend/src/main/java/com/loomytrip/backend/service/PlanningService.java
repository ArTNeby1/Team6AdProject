package com.loomytrip.backend.service;

import com.loomytrip.backend.client.AiPlanItineraryResult;
import com.loomytrip.backend.client.AiPlanningClient;
import com.loomytrip.backend.client.AiRecommendResult;
import com.loomytrip.backend.client.MapPlacesClient;
import com.loomytrip.backend.dto.request.ConfirmSessionRequest;
import com.loomytrip.backend.dto.request.CreateChatMessageRequest;
import com.loomytrip.backend.dto.request.CreatePlanningSessionRequest;
import com.loomytrip.backend.dto.request.UpdateDraftPlaceRequest;
import com.loomytrip.backend.dto.response.ConfirmSessionResponse;
import com.loomytrip.backend.dto.response.PlanningSessionDetailResponse;
import com.loomytrip.backend.dto.response.SuggestedAdditionResponse;
import com.loomytrip.backend.entity.ChatMessage;
import com.loomytrip.backend.entity.ChatRole;
import com.loomytrip.backend.entity.Destination;
import com.loomytrip.backend.entity.DraftActivity;
import com.loomytrip.backend.entity.DraftPlace;
import com.loomytrip.backend.entity.PlanningSession;
import com.loomytrip.backend.entity.PlanningSessionStatus;
import com.loomytrip.backend.entity.Trip;
import com.loomytrip.backend.entity.TripDay;
import com.loomytrip.backend.entity.TripSchedule;
import com.loomytrip.backend.entity.User;
import com.loomytrip.backend.entity.ValidationStatus;
import com.loomytrip.backend.event.InitialImportRequestedEvent;
import com.loomytrip.backend.exception.ApiException;
import com.loomytrip.backend.mapper.EntityMapper;
import com.loomytrip.backend.repository.ChatMessageRepository;
import com.loomytrip.backend.repository.DraftActivityRepository;
import com.loomytrip.backend.repository.DraftPlaceRepository;
import com.loomytrip.backend.repository.PlanningSessionRepository;
import com.loomytrip.backend.repository.TripDayRepository;
import com.loomytrip.backend.repository.TripRepository;
import com.loomytrip.backend.repository.TripScheduleRepository;
import com.loomytrip.backend.repository.UserRepository;
import com.loomytrip.backend.util.SecurityUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanningService {

    private static final String DEFAULT_DESTINATION = "Singapore";
    private static final int NOTE_MAX_LENGTH = 255;
    private static final int DEFAULT_VISIT_SLOT_MINUTES = 90;
    /** Mirrors ML's itinerary_planner.MAX_DAYS / trip_models.MAX_DURATION_DAYS — see
     * ML/docs/handoff_duration_2026-08-14.md. Kept in sync manually since it's a small,
     * cross-service constant, not worth a shared config for. */
    private static final int MAX_DURATION_DAYS = 30;

    private final PlanningSessionRepository planningSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DraftPlaceRepository draftPlaceRepository;
    private final DraftActivityRepository draftActivityRepository;
    private final UserRepository userRepository;
    private final DestinationService destinationService;
    private final TripRepository tripRepository;
    private final TripDayRepository tripDayRepository;
    private final TripScheduleRepository tripScheduleRepository;
    private final EntityMapper entityMapper;
    private final AiPlanningClient aiPlanningClient;
    private final MapPlacesClient mapPlacesClient;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    public PlanningService(
            PlanningSessionRepository planningSessionRepository,
            ChatMessageRepository chatMessageRepository,
            DraftPlaceRepository draftPlaceRepository,
            DraftActivityRepository draftActivityRepository,
            UserRepository userRepository,
            DestinationService destinationService,
            TripRepository tripRepository,
            TripDayRepository tripDayRepository,
            TripScheduleRepository tripScheduleRepository,
            EntityMapper entityMapper,
            AiPlanningClient aiPlanningClient,
            MapPlacesClient mapPlacesClient,
            NotificationService notificationService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.planningSessionRepository = planningSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.draftPlaceRepository = draftPlaceRepository;
        this.draftActivityRepository = draftActivityRepository;
        this.userRepository = userRepository;
        this.destinationService = destinationService;
        this.tripRepository = tripRepository;
        this.tripDayRepository = tripDayRepository;
        this.tripScheduleRepository = tripScheduleRepository;
        this.entityMapper = entityMapper;
        this.aiPlanningClient = aiPlanningClient;
        this.mapPlacesClient = mapPlacesClient;
        this.notificationService = notificationService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<com.loomytrip.backend.dto.response.PlanningSessionSummaryResponse> listMySessions() {
        return planningSessionRepository.findByUser_IdOrderByUpdatedAtDesc(currentUser().getId()).stream()
                .map(entityMapper::toPlanningSessionSummary)
                .toList();
    }

    @Transactional
    public PlanningSessionDetailResponse createSession(CreatePlanningSessionRequest request) {
        PlanningSession session = new PlanningSession();
        session.setUser(currentUser());
        session.setTitle(request.title());
        session.setInitialBrief(request.initialBrief());
        boolean hasBrief = request.initialBrief() != null && !request.initialBrief().isBlank();
        session.setStatus(hasBrief ? PlanningSessionStatus.PROCESSING : PlanningSessionStatus.ACTIVE);
        PlanningSession saved = planningSessionRepository.save(session);

        if (hasBrief) {
            ChatMessage briefMessage = new ChatMessage();
            briefMessage.setSession(saved);
            briefMessage.setRole(ChatRole.user);
            briefMessage.setContent(request.initialBrief());
            chatMessageRepository.save(briefMessage);
            eventPublisher.publishEvent(new InitialImportRequestedEvent(saved.getId()));
        }

        return loadSessionDetail(saved.getId());
    }

    /**
     * Handles an initial brief after the create request has committed, so clients can leave
     * the Import page and use the notification/session APIs to resume when it completes.
     */
    @Transactional
    public void processInitialImport(Long sessionId) {
        PlanningSession session = planningSessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.getStatus() != PlanningSessionStatus.PROCESSING) {
            return;
        }
        try {
            Map<String, Object> result = aiPlanningClient.extractTravelInfo(session.getInitialBrief(), null);
            if (outOfScopeDestination(result) != null) {
                result = aiPlanningClient.extractTravelInfo(session.getInitialBrief(), null);
            }
            validateExtractionResult(result);
            persistExtraction(session, result);
            if (session.getStatus() != PlanningSessionStatus.DRAFT_READY) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "AI_NO_PLACES",
                        "AI returned no usable places from your travel notes"
                );
            }
            notificationService.createImportNotification(session, true, null);
        } catch (Exception exception) {
            session.setStatus(PlanningSessionStatus.FAILED);
            session.setFailureReason(safeImportFailureReason(exception));
            planningSessionRepository.save(session);
            notificationService.createImportNotification(
                    session,
                    false,
                    "We could not finish importing your travel notes. Please review the content and try again."
            );
        }
    }

    @Transactional
    public void addMessage(Long sessionId, CreateChatMessageRequest request) {
        PlanningSession session = loadOwnedSession(sessionId);
        ChatMessage message = new ChatMessage();
        message.setSession(session);
        message.setRole(request.role());
        message.setContent(request.content());
        chatMessageRepository.save(message);
    }

    @Transactional(readOnly = true)
    public PlanningSessionDetailResponse getSession(Long sessionId) {
        loadOwnedSession(sessionId);
        return loadSessionDetail(sessionId);
    }

    /**
     * Multi-turn refinement (F-03): re-runs extraction over the full chat history and
     * replaces this session's draft places. Editing a place directly (PUT/DELETE) never
     * comes through here — only new free-text messages do (ai_contract.md section 2.2).
     */
    @Transactional
    public PlanningSessionDetailResponse refineWithAi(Long sessionId) {
        PlanningSession session = loadOwnedSession(sessionId);
        ensureSessionEditable(session);

        List<Map<String, String>> messages = chatMessageRepository.findBySession_IdOrderByCreatedAtAsc(sessionId)
                .stream()
                .map(m -> Map.of("role", m.getRole().name(), "content", m.getContent()))
                .toList();

        Map<String, Object> result = aiPlanningClient.refineFromChat(messages, buildPreferenceText(currentUser()));
        validateExtractionResult(result);
        if (outOfScopeDestination(result) != null) {
            // 同 createSession() 的理由：先重试一次，避免误判。
            result = aiPlanningClient.refineFromChat(messages, buildPreferenceText(currentUser()));
            validateExtractionResult(result);
        }

        draftActivityRepository.deleteBySession_Id(sessionId);
        draftPlaceRepository.deleteBySession_Id(sessionId);
        persistExtraction(session, result);

        return loadSessionDetail(sessionId);
    }

    /**
     * Validates each draft place via the map provider, writing back coordinates / address
     * and {@link ValidationStatus}. Places that already have coordinates are marked VALID
     * without a network call.
     */
    @Transactional
    public PlanningSessionDetailResponse validateDraftPlaces(Long sessionId) {
        PlanningSession session = loadOwnedSession(sessionId);
        ensureSessionEditable(session);
        List<DraftPlace> places = draftPlaceRepository.findBySession_Id(sessionId);

        for (DraftPlace place : places) {
            if (place.getLatitude() != null && place.getLongitude() != null) {
                place.setValidationStatus(ValidationStatus.VALID);
                if (place.getDestination() == null) {
                    place.setDestination(destinationService.findOrCreateByName(
                            place.getName(), place.getCategory(), place.getLatitude(), place.getLongitude()
                    ));
                }
                draftPlaceRepository.save(place);
                continue;
            }

            var match = mapPlacesClient.validatePlace(place.getName(), place.getAddress());
            if (match.isEmpty()) {
                place.setValidationStatus(ValidationStatus.INVALID);
                draftPlaceRepository.save(place);
                continue;
            }

            MapPlacesClient.PlaceMatch placeMatch = match.get();
            place.setAddress(placeMatch.address());
            place.setLatitude(placeMatch.latitude());
            place.setLongitude(placeMatch.longitude());
            place.setValidationStatus(ValidationStatus.VALID);
            place.setDestination(destinationService.findOrCreateByName(
                    place.getName(),
                    place.getCategory(),
                    placeMatch.latitude(),
                    placeMatch.longitude()
            ));
            draftPlaceRepository.save(place);

            // Nominatim asks for max ~1 request/second for the public instance.
            try {
                Thread.sleep(1100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return loadSessionDetail(session.getId());
    }

    /**
     * F-18: sends the user's confirmed draft places to the AI, then persists the result as
     * a Trip.
     *
     * <p>Day count precedence: {@code request.durationDays()} (the frontend asked the user
     * directly) beats {@code session.getDurationDays()} (the AI picked it up from the
     * user's text during extraction — see {@link #parseDurationDays}) beats neither being
     * present, which is a {@code DAYS_REQUIRED} error telling the frontend to prompt the
     * user and retry with {@code request.durationDays()} set. There's deliberately no
     * silent default (e.g. always 1) — guessing a day count the user never agreed to would
     * mean confirming a trip shorter or longer than what they actually asked for.
     *
     * <p>1 day still goes through {@code /recommend} (weather-aware ordering, plus
     * suggested_additions the frontend shows in the post-confirm summary — see
     * ai_contract.md 2.4). More than 1 day switches to {@code /plan-itinerary}, which
     * route-first/split-second groups stops by day so nearby places land together instead
     * of each day crossing the whole island; that endpoint doesn't return
     * suggested_additions (a `/recommend`-only field), so multi-day confirmations just don't
     * have any — the frontend already treats that list as optional.
     */
    @Transactional
    public ConfirmSessionResponse confirmSession(Long sessionId, ConfirmSessionRequest request) {
        PlanningSession session = loadOwnedSession(sessionId);
        ensureSessionReadyToConfirm(session);
        List<DraftPlace> draftPlaces = draftPlaceRepository.findBySession_Id(sessionId);
        if (draftPlaces.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_PLACES", "Session has no confirmed places to plan");
        }

        Integer requestedDays = request == null ? null : request.durationDays();
        Integer numDays = requestedDays != null ? requestedDays : session.getDurationDays();
        if (numDays == null) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "DAYS_REQUIRED",
                    "How many days is this trip? Ask the user and retry with durationDays set."
            );
        }
        if (numDays < 1 || numDays > MAX_DURATION_DAYS) {
            // 上限跟 ML itinerary_planner.MAX_DAYS 对齐（见 ML/schema/trip_models.py）——
            // 这里提前挡住，不然会在调 /plan-itinerary 时才被拒（AiPlanningClientHttp
            // 把那个 4xx 降级成空 days，最后报出来的是容易让人误会的
            // AI_SERVICE_UNAVAILABLE，不如现在就说清楚是天数不对。
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_DURATION",
                    "durationDays must be between 1 and " + MAX_DURATION_DAYS
            );
        }

        Map<Long, List<DraftActivity>> activitiesByPlaceId = groupActivitiesByPlace(sessionId);
        List<Map<String, Object>> places = draftPlaces.stream()
                .map(place -> toAiPlace(place, activitiesByPlaceId.getOrDefault(place.getId(), List.of())))
                .toList();

        ensureDraftPlacesValidated(draftPlaces);

        User user = currentUser();
        LocalDate startDate = LocalDate.now();

        Trip trip = new Trip();
        trip.setUser(user);
        trip.setTripName(session.getTitle() != null ? session.getTitle() : "Trip");
        trip.setStartDate(startDate);
        trip.setDurationDays(numDays);
        Trip savedTrip = tripRepository.save(trip);

        String weatherSummary;
        List<SuggestedAdditionResponse> suggestions;

        if (numDays == 1) {
            AiRecommendResult result = aiPlanningClient.recommend(places, startDate.toString(), buildPreferenceText(user));
            List<AiRecommendResult.OrderedStop> orderedStops = resolveOrderedStops(result, places);

            TripDay day = new TripDay();
            day.setTrip(savedTrip);
            day.setDaySequence(1);
            TripDay savedDay = tripDayRepository.save(day);
            writeDaySchedules(savedDay, orderedStops.stream().map(this::toStopView).toList());

            weatherSummary = result.weatherSummary();
            suggestions = result.suggestedAdditions().stream()
                    .map(s -> new SuggestedAdditionResponse(
                            s.name(), s.type(), s.lat(), s.lng(), s.distanceKm(), s.reason(), s.activities()
                    ))
                    .toList();
        } else {
            AiPlanItineraryResult result = aiPlanningClient.planItinerary(places, startDate.toString(), numDays);
            if (result.days().isEmpty()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_SERVICE_UNAVAILABLE", "Itinerary planning service is unavailable");
            }
            for (AiPlanItineraryResult.PlannedDay plannedDay : result.days()) {
                TripDay day = new TripDay();
                day.setTrip(savedTrip);
                day.setDaySequence(plannedDay.day());
                TripDay savedDay = tripDayRepository.save(day);
                writeDaySchedules(savedDay, plannedDay.stops().stream().map(this::toStopView).toList());
            }
            weatherSummary = result.days().get(0).weatherSummary();
            suggestions = List.of();
        }

        session.setConfirmedTrip(savedTrip);
        session.setStatus(PlanningSessionStatus.CONFIRMED);
        session.setDurationDays(numDays);
        planningSessionRepository.save(session);

        return new ConfirmSessionResponse(
                savedTrip.getId(),
                savedTrip.getTripName(),
                savedTrip.getStartDate(),
                savedTrip.getDurationDays(),
                savedTrip.getUpdatedAt(),
                weatherSummary,
                suggestions
        );
    }

    /** Common shape `writeDaySchedules` needs from either AI response type (single-day
     * `/recommend`'s OrderedStop or multi-day `/plan-itinerary`'s PlannedStop) — same fields,
     * different Java records, so this lets one write-loop serve both branches of
     * confirmSession. */
    private record StopView(String name, String type, BigDecimal lat, BigDecimal lng, String timeOfDay, String reason) {
    }

    private StopView toStopView(AiRecommendResult.OrderedStop stop) {
        return new StopView(stop.name(), stop.type(), stop.lat(), stop.lng(), stop.timeOfDay(), stop.reason());
    }

    private StopView toStopView(AiPlanItineraryResult.PlannedStop stop) {
        return new StopView(stop.name(), stop.type(), stop.lat(), stop.lng(), stop.timeOfDay(), stop.reason());
    }

    /** Persists one day's stops as TripSchedule rows. First stop of the day is pinned to
     * 09:00 regardless of the AI's time-of-day suggestion — same rule as
     * TripService#addSchedules, see the comment there for why. */
    private void writeDaySchedules(TripDay savedDay, List<StopView> stops) {
        int sequence = 1;
        LocalTime clockCursor = LocalTime.of(9, 0);
        boolean isFirstStopOfDay = true;
        for (StopView stop : stops) {
            Destination destination = destinationService.findOrCreateByName(stop.name(), stop.type(), stop.lat(), stop.lng());
            if (isFirstStopOfDay) {
                isFirstStopOfDay = false;
            } else {
                clockCursor = nextStartTime(clockCursor, stop.timeOfDay());
            }
            TripSchedule schedule = new TripSchedule();
            schedule.setTripDay(savedDay);
            schedule.setDestination(destination);
            schedule.setSequence(sequence++);
            schedule.setLocked(false);
            schedule.setNote(truncate(stop.reason()));
            schedule.setStartTime(clockCursor);
            tripScheduleRepository.save(schedule);
            clockCursor = clockCursor.plusMinutes(DEFAULT_VISIT_SLOT_MINUTES);
        }
    }

    @Transactional
    public void updateDraftPlace(Long placeId, UpdateDraftPlaceRequest request) {
        DraftPlace place = draftPlaceRepository.findById(placeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLACE_NOT_FOUND", "Draft place not found"));
        ensureSessionOwner(place.getSession());
        ensureSessionEditable(place.getSession());

        if (request.name() != null) {
            place.setName(request.name());
        }
        if (request.address() != null) {
            place.setAddress(request.address());
        }
        if (request.latitude() != null) {
            place.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            place.setLongitude(request.longitude());
        }
        if (request.category() != null) {
            place.setCategory(request.category());
        }
        if (request.note() != null) {
            place.setNote(request.note());
        }
        draftPlaceRepository.save(place);
    }

    @Transactional
    public void deleteDraftPlace(Long placeId) {
        DraftPlace place = draftPlaceRepository.findById(placeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLACE_NOT_FOUND", "Draft place not found"));
        ensureSessionOwner(place.getSession());
        ensureSessionEditable(place.getSession());
        draftPlaceRepository.delete(place);
    }

    // ---------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------

    /**
     * Returns the offending {@code destination} string if the extraction landed outside
     * Singapore, or {@code null} if it's in scope (blank/missing destination counts as
     * in-scope — the AI just didn't say). Split out of {@link #persistExtraction} so
     * {@link #createSession} can check it without committing to throwing.
     *
     * <p>Product rule: "any landmark that exists in Singapore counts as Singapore" — this
     * app only ever plans Singapore trips, so a global-namesake landmark should resolve to
     * its Singapore instance, never get flagged as foreign. Bedrock's {@code destination}
     * field is noisy on short, landmark-only notes (e.g. "Gardens by the Bay this
     * morning...") — it sometimes echoes back the landmark name instead of inferring
     * "Singapore". A plain substring check on that string would wrongly reject those, so
     * when it doesn't literally say "Singapore" we geocode the string itself, scoped to
     * Singapore (same {@code countrycodes=sg} restriction as every other place lookup —
     * see MapPlacesClientHttp). A hit means the name genuinely exists here (Gardens by the
     * Bay does); a miss means it's actually foreign (Tokyo Tower doesn't).
     */
    private String outOfScopeDestination(Map<String, Object> result) {
        Object destinationValue = result.get("destination");
        if (!(destinationValue instanceof String destination) || destination.isBlank()) {
            return null;
        }
        if (destination.toLowerCase(java.util.Locale.ROOT).contains(DEFAULT_DESTINATION.toLowerCase(java.util.Locale.ROOT))) {
            return null;
        }
        if (mapPlacesClient.existsNotablyInSingapore(destination)) {
            return null;
        }
        return destination;
    }

    /**
     * Reads an optional {@code duration_days} int out of an extraction result — ML doesn't
     * populate this yet (as of this writing the extraction schema/prompt hasn't been
     * updated for it), this is written ahead of that so the rest of the day-count flow
     * ({@link PlanningSession#getDurationDays()}, {@code ConfirmSessionRequest#durationDays})
     * already works the moment it does; until then every session's days stay null and
     * confirmSession() falls through to requiring the frontend to ask. Missing/non-numeric/
     * non-positive just means "AI didn't say", not an error.
     */
    private static Integer parseDurationDays(Map<String, Object> result) {
        Object value = result.get("duration_days");
        if (value instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        return null;
    }

    /**
     * Persists an extraction result (`/extract-travel-info` or `/refine` — same JSON
     * shape) as DraftPlace/DraftActivity rows. Rejects out-of-scope destinations
     * (ML/docs/ai_contract.md section 6, item 6).
     */
    @SuppressWarnings("unchecked")
    private void persistExtraction(PlanningSession session, Map<String, Object> result) {
        // ML 侧区分了"用户输入没有可用的旅行信息"（422 NO_USEFUL_CONTENT，见
        // AiPlanningClientHttp#mapExtractionError）跟"AI 服务真的挂了"——前者是输入
        // 问题，该告诉用户"重新描述一下你的行程"，不是含糊的 AI_SERVICE_UNAVAILABLE，
        // 不然用户会以为是服务故障去反复重试，而不是去改自己写的内容。
        if ("NO_USEFUL_CONTENT".equals(result.get("status"))) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "NO_USEFUL_CONTENT",
                    String.valueOf(result.getOrDefault("message", "Couldn't find any travel info in that text — try describing where you went or want to go."))
            );
        }

        String outOfScopeDestination = outOfScopeDestination(result);
        if (outOfScopeDestination != null) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "OUT_OF_SCOPE",
                    "This app only plans trips within Singapore (got: " + outOfScopeDestination + ")"
            );
        }

        // 只在这次抽取真的给出了天数时才覆盖——refine 追加一句话时 AI 不一定每次都
        // 重新报一遍天数，缺席不代表用户改主意了，不要把已经知道的值清掉。
        Integer parsedDurationDays = parseDurationDays(result);
        if (parsedDurationDays != null) {
            session.setDurationDays(parsedDurationDays);
            planningSessionRepository.save(session);
        }

        Object placesValue = result.get("places");
        if (!(placesValue instanceof List<?> rawPlaces) || rawPlaces.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "AI_NO_PLACES",
                    "AI returned no places; existing draft places were kept unchanged"
            );
        }

        int savedPlaces = 0;
        for (Object rawPlace : rawPlaces) {
            if (!(rawPlace instanceof Map<?, ?> placeMap)) {
                continue;
            }
            Map<String, Object> place = (Map<String, Object>) placeMap;
            String name = String.valueOf(place.getOrDefault("name", "")).trim();
            if (name.isEmpty() || "null".equalsIgnoreCase(name)) {
                continue;
            }

            DraftPlace draftPlace = new DraftPlace();
            draftPlace.setSession(session);
            draftPlace.setName(name);
            draftPlace.setCategory((String) place.get("type"));
            draftPlace.setValidationStatus(ValidationStatus.UNVALIDATED);

            mapPlacesClient.validatePlace(draftPlace.getName(), null).ifPresent(match -> {
                draftPlace.setAddress(match.address());
                draftPlace.setLatitude(match.latitude());
                draftPlace.setLongitude(match.longitude());
                draftPlace.setValidationStatus(ValidationStatus.VALID);
            });

            DraftPlace savedPlace = draftPlaceRepository.save(draftPlace);
            savedPlaces++;

            Object activitiesValue = place.get("activities");
            if (activitiesValue instanceof List<?> activities) {
                for (Object activity : activities) {
                    DraftActivity draftActivity = new DraftActivity();
                    draftActivity.setSession(session);
                    draftActivity.setDraftPlace(savedPlace);
                    draftActivity.setTitle(String.valueOf(activity));
                    draftActivityRepository.save(draftActivity);
                }
            }
        }

        if (savedPlaces == 0) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "AI_NO_PLACES",
                    "AI returned no usable places; existing draft places were kept unchanged"
            );
        }

        session.setStatus(PlanningSessionStatus.DRAFT_READY);
        session.setFailureReason(null);
        planningSessionRepository.save(session);
    }

    private PlanningSessionDetailResponse loadSessionDetail(Long sessionId) {
        PlanningSession session = planningSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Planning session not found"));
        List<DraftPlace> places = draftPlaceRepository.findBySession_Id(sessionId);
        Map<Long, List<DraftActivity>> activitiesByPlaceId = groupActivitiesByPlace(sessionId);
        return entityMapper.toPlanningSessionDetail(session, places, activitiesByPlaceId);
    }

    private Map<Long, List<DraftActivity>> groupActivitiesByPlace(Long sessionId) {
        return draftActivityRepository.findBySession_Id(sessionId).stream()
                .filter(a -> a.getDraftPlace() != null)
                .collect(Collectors.groupingBy(a -> a.getDraftPlace().getId()));
    }

    private Map<String, Object> toAiPlace(DraftPlace place, List<DraftActivity> activities) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", place.getName());
        map.put("type", place.getCategory() != null ? place.getCategory() : "other");
        map.put("lat", place.getLatitude());
        map.put("lng", place.getLongitude());
        map.put("activities", activities.stream().map(DraftActivity::getTitle).toList());
        return map;
    }

    private void validateExtractionResult(Map<String, Object> result) {
        if (result == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_SERVICE_UNAVAILABLE", "AI service returned no response");
        }
        Object status = result.get("status");
        if (status != null && !"OK".equalsIgnoreCase(String.valueOf(status))) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "AI_EXTRACTION_FAILED",
                    "AI extraction failed (" + status + "); existing draft places were kept unchanged"
            );
        }
        Object placesValue = result.get("places");
        if (!(placesValue instanceof List<?> rawPlaces) || rawPlaces.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "AI_NO_PLACES",
                    "AI returned no places; existing draft places were kept unchanged"
            );
        }
    }

    private void ensureDraftPlacesValidated(List<DraftPlace> draftPlaces) {
        List<String> invalid = new ArrayList<>();
        for (DraftPlace place : draftPlaces) {
            if (place.getValidationStatus() != ValidationStatus.VALID
                    || !DestinationService.hasUsableCoordinates(place.getLatitude(), place.getLongitude())) {
                invalid.add(place.getName());
            }
        }
        if (!invalid.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "PLACES_NOT_VALIDATED",
                    "Validate places before confirm: " + String.join(", ", invalid)
            );
        }
    }

    @SuppressWarnings("unchecked")
    private List<AiRecommendResult.OrderedStop> resolveOrderedStops(
            AiRecommendResult result,
            List<Map<String, Object>> draftPlaces
    ) {
        if (result != null
                && "OK".equalsIgnoreCase(result.status())
                && !result.orderedStops().isEmpty()) {
            return result.orderedStops();
        }

        List<AiRecommendResult.OrderedStop> fallback = new ArrayList<>();
        int order = 1;
        for (Map<String, Object> place : draftPlaces) {
            Object lat = place.get("lat");
            Object lng = place.get("lng");
            fallback.add(new AiRecommendResult.OrderedStop(
                    String.valueOf(place.get("name")),
                    String.valueOf(place.getOrDefault("type", "other")),
                    lat instanceof BigDecimal bigLat ? bigLat : null,
                    lng instanceof BigDecimal bigLng ? bigLng : null,
                    place.get("activities") instanceof List<?> activities
                            ? (List<String>) activities
                            : List.of(),
                    order++,
                    null,
                    null,
                    "Fallback order (AI recommendation unavailable)"
            ));
        }
        if (fallback.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "RECOMMENDATION_FAILED",
                    "Could not build an itinerary from confirmed places"
            );
        }
        return fallback;
    }

    /**
     * Builds the `preference_text` string the AI `/recommend` and `/refine` endpoints expect
     * (ai_contract.md: "用户偏好，比如 travel_style=culture"), from the user's saved
     * `travel_style`/`prefer_transport` (UserService.updatePreferences). Returns null when
     * neither is set so the AI agent falls back to its no-preference behavior instead of
     * being sent a meaningless empty string.
     */
    private String buildPreferenceText(User user) {
        List<String> parts = new ArrayList<>();
        if (user.getTravelStyle() != null && !user.getTravelStyle().isBlank()) {
            parts.add("travel_style=" + user.getTravelStyle());
        }
        if (user.getPreferTransport() != null && !user.getPreferTransport().isBlank()) {
            parts.add("prefer_transport=" + user.getPreferTransport());
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    /**
     * Converts a stop's `time_of_day` bucket (morning/afternoon/evening/null — see
     * ai_contract.md section 5) into a concrete, strictly-increasing {@link LocalTime} to
     * store on the schedule. {@code time_of_day} is only ever a coarse bucket, never an exact
     * clock time, so this is a display-time heuristic, not something the AI actually decided
     * minute-by-minute: jump forward to the bucket's opening time when the AI's ordering moves
     * into a new part of the day, otherwise just advance the cursor by a default 90-minute
     * visit slot so consecutive stops in the same bucket don't collide on the same timestamp.
     */
    private LocalTime nextStartTime(LocalTime cursor, String timeOfDay) {
        LocalTime bucketStart = switch (timeOfDay == null ? "" : timeOfDay.toLowerCase()) {
            case "morning" -> LocalTime.of(9, 0);
            case "afternoon" -> LocalTime.of(13, 0);
            case "evening" -> LocalTime.of(18, 0);
            default -> cursor;
        };
        LocalTime next = bucketStart.isAfter(cursor) ? bucketStart : cursor;
        return next.isBefore(LocalTime.of(22, 30)) ? next : LocalTime.of(22, 30);
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > NOTE_MAX_LENGTH ? value.substring(0, NOTE_MAX_LENGTH) : value;
    }

    private static String safeImportFailureReason(Exception exception) {
        if (exception instanceof ApiException apiException) {
            return switch (apiException.getCode()) {
                case "NO_USEFUL_CONTENT" -> "We could not find usable travel details in that text.";
                case "OUT_OF_SCOPE" -> "This app only plans trips within Singapore.";
                case "AI_NO_PLACES", "AI_EXTRACTION_FAILED", "AI_SERVICE_UNAVAILABLE" ->
                        "The import service could not extract places right now. Please try again.";
                default -> "We could not finish importing your travel notes.";
            };
        }
        return "We could not finish importing your travel notes.";
    }

    private void ensureSessionEditable(PlanningSession session) {
        if (session.getStatus() == PlanningSessionStatus.PROCESSING) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "IMPORT_IN_PROGRESS",
                    "This import is still processing. Wait for it to finish before editing."
            );
        }
        if (session.getStatus() == PlanningSessionStatus.CONFIRMED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SESSION_ALREADY_CONFIRMED",
                    "This planning session has already been confirmed into a trip."
            );
        }
    }

    private void ensureSessionReadyToConfirm(PlanningSession session) {
        if (session.getStatus() == PlanningSessionStatus.PROCESSING) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "IMPORT_IN_PROGRESS",
                    "This import is still processing. Wait for it to finish before confirming."
            );
        }
        if (session.getStatus() == PlanningSessionStatus.FAILED) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "IMPORT_FAILED",
                    "This import failed. Start a new import with updated travel notes."
            );
        }
        if (session.getStatus() == PlanningSessionStatus.CONFIRMED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SESSION_ALREADY_CONFIRMED",
                    "This planning session has already been confirmed into a trip."
            );
        }
        if (session.getStatus() != PlanningSessionStatus.DRAFT_READY
                && session.getStatus() != PlanningSessionStatus.ACTIVE) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "SESSION_NOT_READY",
                    "This planning session is not ready to confirm."
            );
        }
    }

    private PlanningSession loadOwnedSession(Long sessionId) {
        PlanningSession session = planningSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Planning session not found"));
        ensureSessionOwner(session);
        return session;
    }

    private void ensureSessionOwner(PlanningSession session) {
        if (!session.getUser().getId().equals(currentUser().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Session does not belong to current user");
        }
    }

    private User currentUser() {
        return userRepository.findByEmail(SecurityUtils.currentUserEmail())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "User not found"));
    }
}
