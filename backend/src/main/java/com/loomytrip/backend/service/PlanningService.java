package com.loomytrip.backend.service;

import com.loomytrip.backend.client.AiPlanningClient;
import com.loomytrip.backend.client.AiRecommendResult;
import com.loomytrip.backend.client.MapPlacesClient;
import com.loomytrip.backend.dto.request.ConfirmSessionRequest;
import com.loomytrip.backend.dto.request.CreateChatMessageRequest;
import com.loomytrip.backend.dto.request.CreatePlanningSessionRequest;
import com.loomytrip.backend.dto.request.UpdateDraftActivityRequest;
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
            session.setFailureCode(safeImportFailureCode(exception));
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
            } else {
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
            }

            // Keep a small gap between public geocoder calls (Photon / OSM etiquette). Throttle
            // after every lookup — including failed ones — so one bad response doesn't fire the
            // rest of the batch back-to-back.
            try {
                Thread.sleep(400L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return loadSessionDetail(session.getId());
    }

    /**
     * F-18: persists the user's confirmed draft places as a Trip.
     *
     * <p>Day count precedence: {@code request.durationDays()} (the frontend asked the user
     * directly) beats {@code session.getDurationDays()} (the AI picked it up from the
     * user's text during extraction — see {@link #parseDurationDays}) beats neither being
     * present, which is a {@code DAYS_REQUIRED} error telling the frontend to prompt the
     * user and retry with {@code request.durationDays()} set. There's deliberately no
     * silent default (e.g. always 1) — guessing a day count the user never agreed to would
     * mean confirming a trip shorter or longer than what they actually asked for.
     *
     * <p>Day/time assignment comes from {@code draft_activity.suggested_day}/{@code start_time}
     * — the user's own drag-and-drop arrangement in the import review UI (see
     * {@link #updateDraftActivity}), not a fresh AI re-ordering. A place with no
     * {@code suggested_day} set on any of its activities (never dragged) defaults to day 1
     * rather than being dropped. {@code /recommend} is still called once, purely for
     * {@code weatherSummary}/{@code suggestedAdditions} — its {@code ordered_stops} (the
     * AI's own ordering) is intentionally ignored here.
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

        // 按用户拖拽好的 suggested_day 分组；没拖过的默认分到第 1 天，不会因为没手动
        // 排过就把地点丢掉。
        Map<Integer, List<DraftPlace>> placesByDay = new java.util.TreeMap<>();
        for (DraftPlace place : draftPlaces) {
            int day = Math.max(1, Math.min(resolveSuggestedDay(place, activitiesByPlaceId), numDays));
            placesByDay.computeIfAbsent(day, d -> new ArrayList<>()).add(place);
        }

        for (int dayNum = 1; dayNum <= numDays; dayNum++) {
            TripDay tripDay = new TripDay();
            tripDay.setTrip(savedTrip);
            tripDay.setDaySequence(dayNum);
            TripDay savedDay = tripDayRepository.save(tripDay);

            // 同一天内按用户设置的 start_time 排序；没设置的排在后面，保持原有相对顺序。
            List<DraftPlace> ordered = placesByDay.getOrDefault(dayNum, List.of()).stream()
                    .sorted(java.util.Comparator.comparing(p -> resolveStartTime(p, activitiesByPlaceId)))
                    .toList();

            int sequence = 1;
            LocalTime clockCursor = LocalTime.of(9, 0);
            boolean isFirstStopOfDay = true;
            for (DraftPlace place : ordered) {
                Destination destination = destinationService.findOrCreateByName(
                        place.getName(), place.getCategory(), place.getLatitude(), place.getLongitude());
                LocalTime explicitStart = resolveStartTime(place, activitiesByPlaceId);
                LocalTime startTime;
                if (explicitStart != LocalTime.MAX) {
                    startTime = explicitStart;
                } else if (isFirstStopOfDay) {
                    startTime = LocalTime.of(9, 0);
                } else {
                    startTime = clockCursor;
                }
                clockCursor = startTime.plusMinutes(DEFAULT_VISIT_SLOT_MINUTES);
                isFirstStopOfDay = false;

                TripSchedule schedule = new TripSchedule();
                schedule.setTripDay(savedDay);
                schedule.setDestination(destination);
                schedule.setSequence(sequence++);
                schedule.setLocked(false);
                schedule.setStartTime(startTime);
                schedule.setNote(buildScheduleNote(place, activitiesByPlaceId));
                tripScheduleRepository.save(schedule);
            }
        }

        // 附近推荐/天气单独调一次 /recommend，只取这两块附加信息，排序结果不用
        // （行程结构已经交给用户自己拖拽好的 suggested_day/start_time）。
        AiRecommendResult recommendResult = aiPlanningClient.recommend(places, startDate.toString(), buildPreferenceText(user));
        String weatherSummary = recommendResult.weatherSummary();
        // ML only excludes places already in *this* trip's place list. It has no idea what
        // the traveler already scheduled on OTHER trips (e.g. already been to Sentosa last
        // time), so re-check suggestions here against every destination the user has ever
        // put on any of their trips before handing them to the frontend.
        java.util.Set<String> visitedNames = tripScheduleRepository.findVisitedDestinationNamesByUserId(user.getId());
        List<SuggestedAdditionResponse> suggestions = recommendResult.suggestedAdditions().stream()
                .filter(s -> !visitedNames.contains(s.name().trim().toLowerCase(java.util.Locale.ROOT)))
                .map(s -> new SuggestedAdditionResponse(
                        s.name(), s.type(), s.lat(), s.lng(), s.distanceKm(), s.reason(), s.activities()
                ))
                .toList();

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

    /** {@code trip_schedule.note} per the data dictionary ("draft_activity ... maps to
     * formal trip_day / trip_schedule on confirm") — draft_activity.title (the actual
     * "what to do here" text) was previously dropped entirely at confirm time, only
     * suggested_day/start_time made it through. Prefers the place's own manually-written
     * note (user's exact words win); falls back to the place's activity titles joined
     * together, so AI-extracted detail ("Visit the Supertree Grove; Visit the Cloud
     * Forest") survives into the formal trip instead of vanishing. Null when neither
     * exists — DB column is nullable, no need to force an empty string. */
    private String buildScheduleNote(DraftPlace place, Map<Long, List<DraftActivity>> activitiesByPlaceId) {
        if (place.getNote() != null && !place.getNote().isBlank()) {
            return place.getNote();
        }
        String joined = activitiesByPlaceId.getOrDefault(place.getId(), List.of()).stream()
                .map(DraftActivity::getTitle)
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(title -> !title.isBlank())
                .collect(Collectors.joining("; "));
        if (joined.isBlank()) {
            return null;
        }
        // trip_schedule.note is VARCHAR(255) per the data dictionary — don't let a long
        // activity list blow past the column and fail the insert.
        return joined.length() > 255 ? joined.substring(0, 255) : joined;
    }

    /** Which day this place belongs to: place-level {@code suggested_day} wins (the current
     * drag-and-drop UI writes here — works even for places with zero activities, unlike the
     * old activity-only column), falling back to any activity's {@code suggested_day} for
     * sessions arranged before this column existed. Defaults to day 1 when neither was ever
     * set, same as before. */
    private Integer resolveSuggestedDay(DraftPlace place, Map<Long, List<DraftActivity>> activitiesByPlaceId) {
        if (place.getSuggestedDay() != null) {
            return place.getSuggestedDay();
        }
        return activitiesByPlaceId.getOrDefault(place.getId(), List.of()).stream()
                .map(DraftActivity::getSuggestedDay)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(1);
    }

    /** Same place-first-then-activity precedence as {@link #resolveSuggestedDay}, for
     * {@code start_time}. {@link LocalTime#MAX} means neither was ever set — sorts places
     * nobody has arranged yet to the end of their day, see confirmSession(). */
    private LocalTime resolveStartTime(DraftPlace place, Map<Long, List<DraftActivity>> activitiesByPlaceId) {
        if (place.getStartTime() != null) {
            return place.getStartTime();
        }
        return activitiesByPlaceId.getOrDefault(place.getId(), List.of()).stream()
                .map(DraftActivity::getStartTime)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(LocalTime.MAX);
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
        if (request.suggestedDay() != null) {
            place.setSuggestedDay(request.suggestedDay());
        }
        if (request.startTime() != null) {
            place.setStartTime(request.startTime());
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

    /** Drag-and-drop reorder in the import review UI — confirmSession() groups/schedules
     * by these two fields instead of asking the AI to re-order. */
    @Transactional
    public void updateDraftActivity(Long activityId, UpdateDraftActivityRequest request) {
        DraftActivity activity = draftActivityRepository.findById(activityId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ACTIVITY_NOT_FOUND", "Draft activity not found"));
        ensureSessionOwner(activity.getSession());
        ensureSessionEditable(activity.getSession());

        if (request.suggestedDay() != null) {
            activity.setSuggestedDay(request.suggestedDay());
        }
        if (request.startTime() != null) {
            activity.setStartTime(request.startTime());
        }
        draftActivityRepository.save(activity);
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

            // Same Nominatim throttle as validateDraftPlaces — extraction geocodes every place
            // in one burst, and firing them back-to-back trips the public instance's rate limit,
            // leaving later places UNVALIDATED even though they're real, findable landmarks.
            try {
                Thread.sleep(1100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

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
        // NO_USEFUL_CONTENT 有自己专门的错误码/文案（见 persistExtraction 和
        // safeImportFailureCode），不能在这里被当成"AI 服务故障"笼统吞掉——不然
        // "用户输入无效" 和 "AI 服务真的挂了" 在前端看来永远是同一个错误。
        if (status != null && !"OK".equalsIgnoreCase(String.valueOf(status)) && !"NO_USEFUL_CONTENT".equals(status)) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "AI_EXTRACTION_FAILED",
                    "AI extraction failed (" + status + "); existing draft places were kept unchanged"
            );
        }
        if ("NO_USEFUL_CONTENT".equals(status)) {
            return;
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
     * Stable machine-readable code for {@link PlanningSession#getFailureCode()} — the
     * frontend branches on this (e.g. show a dedicated "your input didn't have any travel
     * info" dialog for NO_USEFUL_CONTENT) instead of string-matching the English sentence
     * in {@link #safeImportFailureReason}. AI_NO_PLACES/AI_EXTRACTION_FAILED/
     * AI_SERVICE_UNAVAILABLE collapse into one IMPORT_FAILED code — from the frontend's
     * perspective those are all just "the service side didn't come through", same dialog.
     */
    private static String safeImportFailureCode(Exception exception) {
        if (exception instanceof ApiException apiException) {
            return switch (apiException.getCode()) {
                case "NO_USEFUL_CONTENT", "OUT_OF_SCOPE" -> apiException.getCode();
                default -> "IMPORT_FAILED";
            };
        }
        return "IMPORT_FAILED";
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
