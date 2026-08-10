package com.loomytrip.backend.service;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripService {

    /** Staging offset for bulkUpdateSchedules's two-pass reorder — see its Javadoc. */
    private static final int TEMP_SEQUENCE_OFFSET = 1_000_000;

    private final TripRepository tripRepository;
    private final TripDayRepository tripDayRepository;
    private final TripScheduleRepository tripScheduleRepository;
    private final TripPreferenceRepository tripPreferenceRepository;
    private final UserRepository userRepository;
    private final DestinationService destinationService;
    private final EntityMapper entityMapper;

    public TripService(
            TripRepository tripRepository,
            TripDayRepository tripDayRepository,
            TripScheduleRepository tripScheduleRepository,
            TripPreferenceRepository tripPreferenceRepository,
            UserRepository userRepository,
            DestinationService destinationService,
            EntityMapper entityMapper
    ) {
        this.tripRepository = tripRepository;
        this.tripDayRepository = tripDayRepository;
        this.tripScheduleRepository = tripScheduleRepository;
        this.tripPreferenceRepository = tripPreferenceRepository;
        this.userRepository = userRepository;
        this.destinationService = destinationService;
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

        int nextSequence = tripScheduleRepository.findByTripDay_IdOrderBySequenceAsc(tripDay.getId()).size() + 1;
        for (String name : request.locationNames()) {
            Destination destination = destinationService.findOrCreateByName(name, null, null, null);
            TripSchedule schedule = new TripSchedule();
            schedule.setTripDay(tripDay);
            schedule.setDestination(destination);
            schedule.setSequence(nextSequence++);
            schedule.setLocked(false);
            tripScheduleRepository.save(schedule);
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
