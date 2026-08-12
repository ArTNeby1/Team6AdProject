package com.loomytrip.backend.service;

import com.loomytrip.backend.client.AiPlanningClient;
import com.loomytrip.backend.client.AiRecommendResult;
import com.loomytrip.backend.dto.request.AddTripScheduleRequest;
import com.loomytrip.backend.dto.request.BulkUpdateSchedulesRequest;
import com.loomytrip.backend.dto.request.CreateTripRequest;
import com.loomytrip.backend.dto.request.UpdateTripRequest;
import com.loomytrip.backend.dto.response.TripSummaryResponse;
import com.loomytrip.backend.entity.Destination;
import com.loomytrip.backend.entity.Trip;
import com.loomytrip.backend.entity.TripDay;
import com.loomytrip.backend.entity.TripPreference;
import com.loomytrip.backend.entity.TripSchedule;
import com.loomytrip.backend.entity.User;
import com.loomytrip.backend.exception.ApiException;
import com.loomytrip.backend.mapper.EntityMapper;
import com.loomytrip.backend.repository.TripDayRepository;
import com.loomytrip.backend.repository.TripPreferenceRepository;
import com.loomytrip.backend.repository.TripRepository;
import com.loomytrip.backend.repository.TripScheduleRepository;
import com.loomytrip.backend.repository.UserRepository;
import com.loomytrip.backend.util.SecurityUtils;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripService {

    /** Staging offset for bulkUpdateSchedules's two-pass reorder — see its Javadoc. */
    private static final int TEMP_SEQUENCE_OFFSET = 1_000_000;
    /** Default gap between consecutive stops when spacing out AI-timed additions — see
     * addSchedules()'s Javadoc and PlanningService's identical constant. */
    private static final int DEFAULT_VISIT_SLOT_MINUTES = 90;

    private final TripRepository tripRepository;
    private final TripDayRepository tripDayRepository;
    private final TripScheduleRepository tripScheduleRepository;
    private final TripPreferenceRepository tripPreferenceRepository;
    private final UserRepository userRepository;
    private final DestinationService destinationService;
    private final EntityMapper entityMapper;
    private final AiPlanningClient aiPlanningClient;

    public TripService(
            TripRepository tripRepository,
            TripDayRepository tripDayRepository,
            TripScheduleRepository tripScheduleRepository,
            TripPreferenceRepository tripPreferenceRepository,
            UserRepository userRepository,
            DestinationService destinationService,
            EntityMapper entityMapper,
            AiPlanningClient aiPlanningClient
    ) {
        this.tripRepository = tripRepository;
        this.tripDayRepository = tripDayRepository;
        this.tripScheduleRepository = tripScheduleRepository;
        this.tripPreferenceRepository = tripPreferenceRepository;
        this.userRepository = userRepository;
        this.destinationService = destinationService;
        this.entityMapper = entityMapper;
        this.aiPlanningClient = aiPlanningClient;
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
     * Placeholder for F-09 AI itinerary generation.
     */
    public Object generateItinerary(Long tripId) {
        getTrip(tripId);
        throw new ApiException(
                HttpStatus.NOT_IMPLEMENTED,
                "NOT_IMPLEMENTED",
                "Itinerary generation will call AiPlanningClient in a later iteration"
        );
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

    /**
     * Converts a stop's `time_of_day` bucket (morning/afternoon/evening/null) into a concrete,
     * strictly-increasing {@link LocalTime}. Mirrors PlanningService.nextStartTime() — see its
     * Javadoc for the reasoning (time_of_day is a coarse bucket, not an exact clock time).
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
