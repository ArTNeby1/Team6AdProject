package com.loomytrip.backend.mapper;

import com.loomytrip.backend.dto.response.DestinationResponse;
import com.loomytrip.backend.dto.response.DraftActivityResponse;
import com.loomytrip.backend.dto.response.DraftPlaceResponse;
import com.loomytrip.backend.dto.response.PlanningSessionDetailResponse;
import com.loomytrip.backend.dto.response.PlanningSessionSummaryResponse;
import com.loomytrip.backend.dto.response.TripScheduleResponse;
import com.loomytrip.backend.dto.response.TripSummaryResponse;
import com.loomytrip.backend.entity.Destination;
import com.loomytrip.backend.entity.DraftActivity;
import com.loomytrip.backend.entity.DraftPlace;
import com.loomytrip.backend.entity.PlanningSession;
import com.loomytrip.backend.entity.Trip;
import com.loomytrip.backend.entity.TripPreference;
import com.loomytrip.backend.entity.TripSchedule;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class EntityMapper {

    public TripSummaryResponse toTripSummary(Trip trip, TripPreference preference, List<TripSchedule> schedules) {
        return new TripSummaryResponse(
                trip.getId(),
                trip.getTripName(),
                trip.getStartDate(),
                trip.getDurationDays(),
                trip.getUpdatedAt(),
                deriveTripStatus(trip),
                preference == null ? null : preference.getTravelStyle(),
                preference == null ? null : preference.getPreferTransport(),
                trip.isFavorite(),
                schedules.stream().map(this::toTripSchedule).toList()
        );
    }

    /**
     * No `status` column on `trip` (see TripSummaryResponse) — derived from today vs.
     * the trip's date range every time it's read, per product decision (2026-08-09):
     * no manual "mark as finished" for now.
     */
    private String deriveTripStatus(Trip trip) {
        LocalDate today = LocalDate.now();
        LocalDate start = trip.getStartDate();
        LocalDate end = start.plusDays(Math.max(trip.getDurationDays() - 1, 0));
        if (today.isBefore(start)) {
            return "NOT_STARTED";
        }
        if (today.isAfter(end)) {
            return "FINISHED";
        }
        return "ACTIVE";
    }

    public TripScheduleResponse toTripSchedule(TripSchedule schedule) {
        return new TripScheduleResponse(
                schedule.getId(),
                toDestination(schedule.getDestination()),
                new TripScheduleResponse.TripDayInfo(schedule.getTripDay().getId(), schedule.getTripDay().getDaySequence()),
                schedule.getSequence(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getPlannedDurationMinutes(),
                schedule.getNote()
        );
    }

    public PlanningSessionSummaryResponse toPlanningSessionSummary(PlanningSession session) {
        Long tripId = session.getConfirmedTrip() == null ? null : session.getConfirmedTrip().getId();
        return new PlanningSessionSummaryResponse(
                session.getId(),
                session.getTitle(),
                session.getInitialBrief(),
                session.getStatus(),
                tripId,
                session.getUpdatedAt()
        );
    }

    public DraftActivityResponse toDraftActivity(DraftActivity activity) {
        return new DraftActivityResponse(activity.getId(), activity.getTitle());
    }

    public DraftPlaceResponse toDraftPlace(DraftPlace place, List<DraftActivity> activities) {
        return new DraftPlaceResponse(
                place.getId(),
                place.getName(),
                place.getAddress(),
                place.getLatitude(),
                place.getLongitude(),
                place.getCategory(),
                place.getValidationStatus(),
                place.getNote(),
                activities.stream().map(this::toDraftActivity).toList()
        );
    }

    public PlanningSessionDetailResponse toPlanningSessionDetail(
            PlanningSession session,
            List<DraftPlace> places,
            Map<Long, List<DraftActivity>> activitiesByPlaceId
    ) {
        Long tripId = session.getConfirmedTrip() == null ? null : session.getConfirmedTrip().getId();
        List<DraftPlaceResponse> placeResponses = places.stream()
                .map(place -> toDraftPlace(place, activitiesByPlaceId.getOrDefault(place.getId(), List.of())))
                .toList();
        return new PlanningSessionDetailResponse(
                session.getId(),
                session.getTitle(),
                session.getInitialBrief(),
                session.getStatus(),
                tripId,
                placeResponses,
                session.getUpdatedAt()
        );
    }

    public DestinationResponse toDestination(Destination destination) {
        return new DestinationResponse(
                destination.getId(),
                destination.getName(),
                destination.getAddress(),
                destination.getLatitude(),
                destination.getLongitude(),
                destination.getCategory()
        );
    }
}
