package com.loomytrip.backend.service;

import com.loomytrip.backend.client.AiPlanItineraryResult;
import com.loomytrip.backend.client.AiPlanningClient;
import com.loomytrip.backend.client.AiRecommendResult;
import com.loomytrip.backend.client.RoutingClient;
import com.loomytrip.backend.dto.request.AddTripScheduleRequest;
import com.loomytrip.backend.dto.request.BulkUpdateSchedulesRequest;
import com.loomytrip.backend.dto.request.CreateTripRequest;
import com.loomytrip.backend.dto.request.UpdateTripRequest;
import com.loomytrip.backend.dto.response.GenerateItineraryResponse;
import com.loomytrip.backend.dto.response.ShareTripResponse;
import com.loomytrip.backend.dto.response.TripRouteResponse;
import com.loomytrip.backend.dto.response.TripSummaryResponse;
import com.loomytrip.backend.dto.response.TripTransportResponse;
import com.loomytrip.backend.entity.Destination;
import com.loomytrip.backend.entity.Trip;
import com.loomytrip.backend.entity.TripDay;
import com.loomytrip.backend.entity.TripPreference;
import com.loomytrip.backend.entity.TripSchedule;
import com.loomytrip.backend.entity.TripTransport;
import com.loomytrip.backend.entity.User;
import com.loomytrip.backend.exception.ApiException;
import com.loomytrip.backend.mapper.EntityMapper;
import com.loomytrip.backend.repository.TripDayRepository;
import com.loomytrip.backend.repository.TripPreferenceRepository;
import com.loomytrip.backend.repository.TripRepository;
import com.loomytrip.backend.repository.TripScheduleRepository;
import com.loomytrip.backend.repository.TripTransportRepository;
import com.loomytrip.backend.repository.UserRepository;
import com.loomytrip.backend.util.SecurityUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripService {

    /** Staging offset for bulkUpdateSchedules's two-pass reorder — see its Javadoc. */
    private static final int TEMP_SEQUENCE_OFFSET = 1_000_000;
    private static final int DEFAULT_VISIT_SLOT_MINUTES = 90;
    private static final int NOTE_MAX_LENGTH = 255;

    private final TripRepository tripRepository;
    private final TripDayRepository tripDayRepository;
    private final TripScheduleRepository tripScheduleRepository;
    private final TripTransportRepository tripTransportRepository;
    private final TripPreferenceRepository tripPreferenceRepository;
    private final UserRepository userRepository;
    private final DestinationService destinationService;
    private final RoutingClient routingClient;
    private final AiPlanningClient aiPlanningClient;
    private final EntityMapper entityMapper;

    public TripService(
            TripRepository tripRepository,
            TripDayRepository tripDayRepository,
            TripScheduleRepository tripScheduleRepository,
            TripTransportRepository tripTransportRepository,
            TripPreferenceRepository tripPreferenceRepository,
            UserRepository userRepository,
            DestinationService destinationService,
            RoutingClient routingClient,
            AiPlanningClient aiPlanningClient,
            EntityMapper entityMapper
    ) {
        this.tripRepository = tripRepository;
        this.tripDayRepository = tripDayRepository;
        this.tripScheduleRepository = tripScheduleRepository;
        this.tripTransportRepository = tripTransportRepository;
        this.tripPreferenceRepository = tripPreferenceRepository;
        this.userRepository = userRepository;
        this.destinationService = destinationService;
        this.routingClient = routingClient;
        this.aiPlanningClient = aiPlanningClient;
        this.entityMapper = entityMapper;
    }

    @Transactional(readOnly = true)
    public List<TripSummaryResponse> listMyTrips() {
        User user = currentUser();
        return tripRepository.findByUser_IdOrderByUpdatedAtDesc(user.getId()).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public TripSummaryResponse createTrip(CreateTripRequest request) {
        User user = currentUser();

        Trip trip = new Trip();
        trip.setUser(user);
        trip.setTripName(request.tripName());
        trip.setStartDate(request.startDate());
        trip.setDurationDays(request.durationDays());
        Trip saved = tripRepository.save(trip);

        List<TripDay> days = new ArrayList<>();
        for (int i = 1; i <= request.durationDays(); i++) {
            TripDay day = new TripDay();
            day.setTrip(saved);
            day.setDaySequence(i);
            days.add(day);
        }
        tripDayRepository.saveAll(days);

        if (request.travelStyle() != null || request.preferTransport() != null) {
            TripPreference preference = new TripPreference();
            preference.setTrip(saved);
            preference.setTravelStyle(request.travelStyle());
            preference.setPreferTransport(request.preferTransport());
            tripPreferenceRepository.save(preference);
        }

        return toSummary(saved);
    }

    @Transactional(readOnly = true)
    public TripSummaryResponse getTrip(Long tripId) {
        return toSummary(loadOwnedTrip(tripId));
    }

    /**
     * Deletes a whole trip. {@code trip_day}/{@code trip_schedule}/{@code trip_transport}/
     * {@code trip_preference} all cascade-delete via FK {@code ON DELETE CASCADE}; a
     * {@code planning_session} that was confirmed into this trip keeps existing —
     * {@code confirmed_trip_id} is {@code ON DELETE SET NULL}, not cascade — so deleting a
     * trip never silently deletes someone's planning session history.
     */
    @Transactional
    public void deleteTrip(Long tripId) {
        Trip trip = loadOwnedTrip(tripId);
        tripRepository.delete(trip);
    }

    /**
     * Partial update (🟠 gap closed): {@code status}/{@code coverImage} still need a schema
     * decision (no columns for them yet). {@code travelStyle}/{@code preferTransport} were
     * write-only until now — `trip_preference` got a row on create but nothing ever read it
     * back (no field on TripSummaryResponse), so it could never actually display.
     */
    @Transactional
    public TripSummaryResponse updateTrip(Long tripId, UpdateTripRequest request) {
        Trip trip = loadOwnedTrip(tripId);

        if (request.tripName() != null && !request.tripName().isBlank()) {
            trip.setTripName(request.tripName());
        }
        if (request.startDate() != null) {
            trip.setStartDate(request.startDate());
        }
        if (request.durationDays() != null && !request.durationDays().equals(trip.getDurationDays())) {
            resizeDays(trip, request.durationDays());
            trip.setDurationDays(request.durationDays());
        }
        if (request.favorite() != null) {
            trip.setFavorite(request.favorite());
        }
        Trip saved = tripRepository.save(trip);

        if (request.travelStyle() != null || request.preferTransport() != null) {
            TripPreference preference = tripPreferenceRepository.findByTrip_Id(tripId)
                    .orElseGet(() -> {
                        TripPreference p = new TripPreference();
                        p.setTrip(saved);
                        return p;
                    });
            if (request.travelStyle() != null) {
                preference.setTravelStyle(request.travelStyle());
            }
            if (request.preferTransport() != null) {
                preference.setPreferTransport(request.preferTransport());
            }
            tripPreferenceRepository.save(preference);
        }

        return toSummary(saved);
    }

    /**
     * Turns on public sharing for a trip: generates a random unique token (idempotent — a
     * second call while already shared just returns the existing token instead of rotating
     * it, so a previously-shared link doesn't silently break). Read-only: the public side
     * ({@link #getSharedTrip}) never lets the token holder mutate anything.
     */
    @Transactional
    public ShareTripResponse shareTrip(Long tripId) {
        Trip trip = loadOwnedTrip(tripId);
        if (trip.getShareToken() == null) {
            trip.setShareToken(generateShareToken());
            tripRepository.save(trip);
        }
        return new ShareTripResponse(trip.getId(), true, trip.getShareToken());
    }

    /** Revokes a trip's public share link — any URL built from the old token 404s afterward. */
    @Transactional
    public ShareTripResponse unshareTrip(Long tripId) {
        Trip trip = loadOwnedTrip(tripId);
        trip.setShareToken(null);
        tripRepository.save(trip);
        return new ShareTripResponse(trip.getId(), false, null);
    }

    /**
     * Public, unauthenticated read of a shared trip (see SecurityConfig — GET
     * /api/v1/public/trips/** is permitAll). Deliberately does NOT go through
     * {@link #loadOwnedTrip} — anyone with the token is allowed to view, that's the point.
     */
    @Transactional(readOnly = true)
    public TripSummaryResponse getSharedTrip(String shareToken) {
        Trip trip = tripRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SHARE_NOT_FOUND", "This share link is invalid or has been revoked"));
        return toSummary(trip);
    }

    private String generateShareToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 🟠 gap closed: adds one or more named stops to a trip day, resolving each name to a
     * real {@code destination} row via {@link DestinationService#findOrCreateByName}
     * (same helper {@code PlanningService.confirmSession} uses for AI-recommended stops).
     *
     * <p>🔴 second gap closed: this is the "import into an existing day" path (Frontend_Web
     * ImportPage.jsx's {@code targetTripId} branch) — unlike {@code confirmSession()}, it
     * never called the AI {@code /recommend} agent at all, so the new stops always landed
     * with {@code start_time = null} and the frontend fell back to displaying a flat 09:00
     * for every one of them, regardless of what the AI would have said about morning/
     * afternoon/evening. Now it asks the same agent, the same way confirmSession() does, and
     * only falls back to the old "just create them in place" behavior if the AI service is
     * unavailable (see AiPlanningClientHttp — network failures degrade to an empty result
     * rather than throwing, so this never blocks the add on the AI being down).
     */
    @Transactional
    public TripSummaryResponse addSchedules(Long tripId, AddTripScheduleRequest request) {
        Trip trip = loadOwnedTrip(tripId);

        if (request.day() > trip.getDurationDays()) {
            resizeDays(trip, request.day());
            trip.setDurationDays(request.day());
            tripRepository.save(trip);
        }

        TripDay tripDay = tripDayRepository.findByTrip_IdAndDaySequence(tripId, request.day())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRIP_DAY_NOT_FOUND", "Trip day not found"));

        List<TripSchedule> existing = tripScheduleRepository.findByTripDay_IdOrderBySequenceAsc(tripDay.getId());
        int nextSequence = existing.size() + 1;
        LocalTime cursor = existing.isEmpty() || existing.get(existing.size() - 1).getStartTime() == null
                ? LocalTime.of(9, 0)
                : existing.get(existing.size() - 1).getStartTime().plusMinutes(DEFAULT_VISIT_SLOT_MINUTES);

        List<Map<String, Object>> aiPlaces = request.locationNames().stream()
                .map(name -> {
                    Map<String, Object> place = new LinkedHashMap<>();
                    place.put("name", name);
                    place.put("type", "other");
                    place.put("activities", List.of());
                    return place;
                })
                .toList();
        LocalDate date = trip.getStartDate() != null ? trip.getStartDate() : LocalDate.now();
        AiRecommendResult result = aiPlanningClient.recommend(aiPlaces, date.toString(), buildPreferenceText(currentUser()));

        if (!result.orderedStops().isEmpty()) {
            for (AiRecommendResult.OrderedStop stop : result.orderedStops()) {
                Destination destination = destinationService.findOrCreateByName(stop.name(), stop.type(), stop.lat(), stop.lng());
                cursor = nextStartTime(cursor, stop.timeOfDay());
                TripSchedule schedule = new TripSchedule();
                schedule.setTripDay(tripDay);
                schedule.setDestination(destination);
                schedule.setSequence(nextSequence++);
                schedule.setLocked(false);
                schedule.setStartTime(cursor);
                tripScheduleRepository.save(schedule);
                cursor = cursor.plusMinutes(DEFAULT_VISIT_SLOT_MINUTES);
            }
        } else {
            // AI service unavailable/degenerate — still add the stops so the import doesn't
            // fail outright, just without AI-determined ordering/timing.
            for (String name : request.locationNames()) {
                Destination destination = destinationService.findOrCreateByName(name, null, null, null);
                TripSchedule schedule = new TripSchedule();
                schedule.setTripDay(tripDay);
                schedule.setDestination(destination);
                schedule.setSequence(nextSequence++);
                schedule.setLocked(false);
                tripScheduleRepository.save(schedule);
            }
        }

        return toSummary(trip);
    }

    /**
     * Bulk reorder/move for schedules that already exist (EditPage.jsx drag-and-drop save,
     * previously a dead comment in TripContext.saveTripEdits — see ML/docs-style handoff
     * note: the reorder never reached the backend, so it never survived a page refresh).
     *
     * <p>Applied in two passes to dodge the {@code (trip_day_id, sequence)} unique
     * constraint: setting final sequences directly can collide mid-transaction when two
     * schedules swap positions (e.g. 1↔2), since MySQL/InnoDB checks uniqueness per
     * statement, not deferred to commit. Parking everything at a distinct temp sequence
     * first (offset by each schedule's own id, so guaranteed unique — {@code sequence} is
     * {@code INT UNSIGNED}, so the offset has to stay positive) sidesteps that entirely.
     */
    @Transactional
    public TripSummaryResponse bulkUpdateSchedules(Long tripId, BulkUpdateSchedulesRequest request) {
        Trip trip = loadOwnedTrip(tripId);

        int maxDay = trip.getDurationDays();
        for (BulkUpdateSchedulesRequest.ScheduleUpdate update : request.schedules()) {
            maxDay = Math.max(maxDay, update.day());
        }
        if (maxDay > trip.getDurationDays()) {
            resizeDays(trip, maxDay);
            trip.setDurationDays(maxDay);
            tripRepository.save(trip);
        }

        List<TripSchedule> schedules = new ArrayList<>();
        for (BulkUpdateSchedulesRequest.ScheduleUpdate update : request.schedules()) {
            TripSchedule schedule = tripScheduleRepository.findById(update.id())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "Schedule not found: " + update.id()));
            if (!schedule.getTripDay().getTrip().getId().equals(tripId)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Schedule does not belong to this trip");
            }
            schedule.setSequence(TEMP_SEQUENCE_OFFSET + schedule.getId().intValue());
            schedules.add(schedule);
        }
        tripScheduleRepository.saveAllAndFlush(schedules);

        Map<Integer, TripDay> daysByNumber = new HashMap<>();
        for (int i = 0; i < schedules.size(); i++) {
            BulkUpdateSchedulesRequest.ScheduleUpdate update = request.schedules().get(i);
            TripDay day = daysByNumber.computeIfAbsent(update.day(), d -> tripDayRepository
                    .findByTrip_IdAndDaySequence(tripId, d)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRIP_DAY_NOT_FOUND", "Trip day not found")));
            schedules.get(i).setTripDay(day);
            schedules.get(i).setSequence(update.sequence());
            if (update.startTime() != null && !update.startTime().isBlank()) {
                try {
                    schedules.get(i).setStartTime(LocalTime.parse(update.startTime()));
                } catch (DateTimeParseException ignored) {
                    // EditPage.jsx's time input is masked to HH:mm, but a half-typed value
                    // could in theory slip through — skip rather than fail the whole
                    // reorder/save over one bad time string.
                }
            }
        }
        tripScheduleRepository.saveAllAndFlush(schedules);

        return toSummary(trip);
    }

    /**
     * 🔴 gap closed: EditPage.jsx's "Delete" button on a location only ever removed it from
     * local draft state — Save then called bulkUpdateSchedules with the remaining schedules,
     * but that endpoint only reorders/moves schedules whose ids are present in the request, it
     * never deletes ones that are missing. There was no endpoint at all for removing a single
     * schedule, so a deleted location always reappeared on the next fetch. See saveTripEdits
     * in TripContext.jsx, which now diffs old vs. new location ids and calls this per removal.
     */
    @Transactional
    public TripSummaryResponse deleteSchedule(Long tripId, Long scheduleId) {
        Trip trip = loadOwnedTrip(tripId);
        TripSchedule schedule = tripScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "Schedule not found: " + scheduleId));
        if (!schedule.getTripDay().getTrip().getId().equals(tripId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Schedule does not belong to this trip");
        }
        tripScheduleRepository.delete(schedule);
        return toSummary(trip);
    }

    /**
     * Builds pairwise driving estimates for one trip day, persists {@code trip_transport}
     * rows, and returns map/navigation data for the frontend.
     */
    @Transactional
    public TripRouteResponse estimateRoute(Long tripId, int day) {
        Trip trip = loadOwnedTrip(tripId);
        if (day < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DAY", "day must be >= 1");
        }

        TripDay tripDay = tripDayRepository.findByTrip_IdAndDaySequence(tripId, day)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRIP_DAY_NOT_FOUND", "Trip day not found"));
        List<TripSchedule> schedules = tripScheduleRepository.findByTripDay_IdOrderBySequenceAsc(tripDay.getId());

        String transportType = tripPreferenceRepository.findByTrip_Id(tripId)
                .map(TripPreference::getPreferTransport)
                .filter(value -> value != null && !value.isBlank())
                .orElse("driving");

        tripTransportRepository.deleteByTripDay_Id(tripDay.getId());

        List<TripRouteResponse.RouteLegResponse> legs = new ArrayList<>();
        List<TripTransportResponse> transports = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        BigDecimal totalDistance = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        int totalMinutes = 0;

        for (int i = 0; i < schedules.size() - 1; i++) {
            TripSchedule from = schedules.get(i);
            TripSchedule to = schedules.get(i + 1);
            String fromLabel = from.getDestination() != null ? from.getDestination().getName() : "Stop " + from.getId();
            String toLabel = to.getDestination() != null ? to.getDestination().getName() : "Stop " + to.getId();

            Destination fromDest = geocodeScheduleDestination(from, warnings);
            Destination toDest = geocodeScheduleDestination(to, warnings);
            if (fromDest == null || toDest == null) {
                continue;
            }

            RoutingClient.RouteEstimate estimate = routingClient.estimate(
                    fromDest.getLatitude(), fromDest.getLongitude(),
                    toDest.getLatitude(), toDest.getLongitude()
            ).orElse(null);
            if (estimate == null) {
                warnings.add("Could not estimate route leg: " + fromLabel + " → " + toLabel);
                continue;
            }

            totalDistance = totalDistance.add(estimate.distanceKm());
            totalMinutes += estimate.durationMinutes();
            legs.add(new TripRouteResponse.RouteLegResponse(
                    from.getId(),
                    to.getId(),
                    fromDest.getName(),
                    toDest.getName(),
                    estimate.distanceKm(),
                    estimate.durationMinutes(),
                    estimate.googleMapLink()
            ));

            TripTransport transport = new TripTransport();
            transport.setTripDay(tripDay);
            transport.setPrevSchedule(from);
            transport.setNextSchedule(to);
            transport.setTransportType(transportType);
            transport.setDistanceKm(estimate.distanceKm());
            transport.setDurationMinutes(estimate.durationMinutes());
            transport.setGoogleMapLink(estimate.googleMapLink());
            transport.setRouteDesc(fromDest.getName() + " → " + toDest.getName());
            TripTransport saved = tripTransportRepository.save(transport);
            transports.add(new TripTransportResponse(
                    saved.getId(),
                    from.getId(),
                    to.getId(),
                    fromDest.getName(),
                    toDest.getName(),
                    transportType,
                    estimate.distanceKm(),
                    estimate.durationMinutes(),
                    estimate.googleMapLink(),
                    transport.getRouteDesc()
            ));
        }

        if (schedules.size() >= 2 && legs.isEmpty()) {
            warnings.add("No route legs could be calculated for this day; check destination coordinates.");
        } else if (schedules.size() >= 2 && legs.size() < schedules.size() - 1) {
            warnings.add("Some route legs were skipped; totals may be incomplete.");
        }

        String googleMapsUrl = buildMultiStopGoogleMapsUrl(schedules);
        return new TripRouteResponse(
                trip.getId(),
                day,
                schedules.size(),
                totalDistance,
                totalMinutes,
                googleMapsUrl,
                legs,
                transports,
                warnings
        );
    }

    private Destination geocodeScheduleDestination(TripSchedule schedule, List<String> warnings) {
        if (schedule.getDestination() == null) {
            warnings.add("Schedule " + schedule.getId() + " has no destination");
            return null;
        }
        try {
            return destinationService.ensureGeocoded(schedule.getDestination());
        } catch (ApiException ex) {
            warnings.add("Could not geocode " + schedule.getDestination().getName() + ": " + ex.getMessage());
            return null;
        }
    }

    private static String buildMultiStopGoogleMapsUrl(List<TripSchedule> schedules) {
        List<TripSchedule> withCoords = schedules.stream()
                .filter(s -> s.getDestination() != null
                        && DestinationService.hasUsableCoordinates(
                                s.getDestination().getLatitude(),
                                s.getDestination().getLongitude()))
                .toList();
        if (withCoords.isEmpty()) {
            return null;
        }
        Destination first = withCoords.get(0).getDestination();
        Destination last = withCoords.get(withCoords.size() - 1).getDestination();
        StringBuilder url = new StringBuilder("https://www.google.com/maps/dir/?api=1")
                .append("&origin=").append(first.getLatitude()).append(",").append(first.getLongitude())
                .append("&destination=").append(last.getLatitude()).append(",").append(last.getLongitude())
                .append("&travelmode=driving");
        if (withCoords.size() > 2) {
            String waypoints = withCoords.subList(1, withCoords.size() - 1).stream()
                    .map(s -> s.getDestination().getLatitude() + "," + s.getDestination().getLongitude())
                    .collect(Collectors.joining("|"));
            url.append("&waypoints=").append(waypoints);
        }
        return url.toString();
    }

    /**
     * F-09: calls ML {@code POST /plan-itinerary} to redistribute existing schedules across
     * trip days, then writes back day/sequence/start_time.
     */
    @Transactional
    public GenerateItineraryResponse generateItinerary(Long tripId) {
        Trip trip = loadOwnedTrip(tripId);
        List<TripSchedule> schedules = tripScheduleRepository
                .findByTripDay_Trip_IdOrderByTripDay_DaySequenceAscSequenceAsc(tripId);
        if (schedules.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_SCHEDULES", "Trip has no stops to plan");
        }

        List<Map<String, Object>> places = new ArrayList<>();
        Map<String, TripSchedule> scheduleByNormalizedName = new HashMap<>();
        for (TripSchedule schedule : schedules) {
            if (schedule.getDestination() == null) {
                continue;
            }
            Destination destination = destinationService.ensureGeocoded(schedule.getDestination());
            scheduleByNormalizedName.put(normalizeName(destination.getName()), schedule);
            places.add(toAiPlace(destination, schedule.getNote()));
        }

        if (places.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_GEOCODED_STOPS", "No stops with resolvable coordinates");
        }

        AiPlanItineraryResult result = aiPlanningClient.planItinerary(
                places,
                trip.getStartDate().toString(),
                trip.getDurationDays()
        );
        if (result == null || result.days() == null || result.days().isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "AI_SERVICE_UNAVAILABLE",
                    "Itinerary planning service is unavailable"
            );
        }

        List<GenerateItineraryResponse.PlannedDayResponse> responseDays = new ArrayList<>();
        java.util.Set<Long> assignedScheduleIds = new java.util.HashSet<>();

        for (AiPlanItineraryResult.PlannedDay plannedDay : result.days()) {
            TripDay tripDay = tripDayRepository.findByTrip_IdAndDaySequence(tripId, plannedDay.day())
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.NOT_FOUND,
                            "TRIP_DAY_NOT_FOUND",
                            "Trip day not found: " + plannedDay.day()
                    ));

            LocalTime clockCursor = LocalTime.of(9, 0);
            List<GenerateItineraryResponse.PlannedStopResponse> stopResponses = new ArrayList<>();
            int sequence = 1;
            for (AiPlanItineraryResult.PlannedStop stop : plannedDay.stops()) {
                TripSchedule schedule = scheduleByNormalizedName.get(normalizeName(stop.name()));
                if (schedule == null) {
                    continue;
                }
                schedule.setTripDay(tripDay);
                schedule.setSequence(sequence++);
                clockCursor = nextStartTime(clockCursor, stop.timeOfDay());
                schedule.setStartTime(clockCursor);
                schedule.setNote(truncate(stop.reason()));
                tripScheduleRepository.save(schedule);
                assignedScheduleIds.add(schedule.getId());
                clockCursor = clockCursor.plusMinutes(DEFAULT_VISIT_SLOT_MINUTES);

                stopResponses.add(new GenerateItineraryResponse.PlannedStopResponse(
                        schedule.getId(),
                        stop.name(),
                        stop.order(),
                        stop.timeOfDay(),
                        stop.reason()
                ));
            }

            responseDays.add(new GenerateItineraryResponse.PlannedDayResponse(
                    plannedDay.day(),
                    plannedDay.date(),
                    plannedDay.weatherSummary(),
                    stopResponses
            ));
        }

        List<TripSchedule> unassigned = schedules.stream()
                .filter(schedule -> !assignedScheduleIds.contains(schedule.getId()))
                .toList();
        if (!unassigned.isEmpty()) {
            TripDay lastDay = tripDayRepository.findByTrip_IdAndDaySequence(tripId, trip.getDurationDays())
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.NOT_FOUND,
                            "TRIP_DAY_NOT_FOUND",
                            "Trip day not found: " + trip.getDurationDays()
                    ));
            int sequence = tripScheduleRepository.findByTripDay_IdOrderBySequenceAsc(lastDay.getId()).size() + 1;
            for (TripSchedule schedule : unassigned) {
                schedule.setTripDay(lastDay);
                schedule.setSequence(sequence++);
                tripScheduleRepository.save(schedule);
                assignedScheduleIds.add(schedule.getId());
            }
        }

        return new GenerateItineraryResponse(
                tripId,
                result.status() != null ? result.status() : "OK",
                responseDays
        );
    }

    private Map<String, Object> toAiPlace(Destination destination, String note) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", destination.getName());
        map.put("type", destination.getCategory() != null ? destination.getCategory() : "attraction");
        map.put("lat", destination.getLatitude());
        map.put("lng", destination.getLongitude());
        map.put("activities", note != null && !note.isBlank() ? List.of(note) : List.of());
        return map;
    }

    private static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    /**
     * Builds the `preference_text` string the AI `/recommend` endpoint expects (ai_contract.md:
     * "用户偏好，比如 travel_style=culture"). Mirrors PlanningService.buildPreferenceText() —
     * kept as a separate copy here rather than shared to avoid coupling the two services over
     * something this small; revisit if a third caller shows up.
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

    private LocalTime nextStartTime(LocalTime cursor, String timeOfDay) {
        LocalTime bucketStart = switch (timeOfDay == null ? "" : timeOfDay.toLowerCase(Locale.ROOT)) {
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

    private TripSummaryResponse toSummary(Trip trip) {
        TripPreference preference = tripPreferenceRepository.findByTrip_Id(trip.getId()).orElse(null);
        List<TripSchedule> schedules = tripScheduleRepository
                .findByTripDay_Trip_IdOrderByTripDay_DaySequenceAscSequenceAsc(trip.getId());
        return entityMapper.toTripSummary(trip, preference, schedules);
    }

    /** Grows or shrinks the trip's `trip_day` rows to match a new duration. Shrinking
     * cascade-deletes that day's schedules (FK ON DELETE CASCADE). */
    private void resizeDays(Trip trip, int newDurationDays) {
        int currentDays = trip.getDurationDays();
        if (newDurationDays > currentDays) {
            List<TripDay> newDays = new ArrayList<>();
            for (int i = currentDays + 1; i <= newDurationDays; i++) {
                TripDay day = new TripDay();
                day.setTrip(trip);
                day.setDaySequence(i);
                newDays.add(day);
            }
            tripDayRepository.saveAll(newDays);
        } else if (newDurationDays < currentDays) {
            tripDayRepository.deleteByTrip_IdAndDaySequenceGreaterThan(trip.getId(), newDurationDays);
        }
    }

    private Trip loadOwnedTrip(Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND", "Trip not found"));
        ensureOwner(trip);
        return trip;
    }

    private User currentUser() {
        return userRepository.findByEmail(SecurityUtils.currentUserEmail())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "User not found"));
    }

    private void ensureOwner(Trip trip) {
        User user = currentUser();
        if (!trip.getUser().getId().equals(user.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Trip does not belong to current user");
        }
    }
}
