package com.loomytrip.backend.mapper;

import com.loomytrip.backend.dto.response.DestinationResponse;
import com.loomytrip.backend.dto.response.PlanningSessionSummaryResponse;
import com.loomytrip.backend.dto.response.TripSummaryResponse;
import com.loomytrip.backend.entity.Destination;
import com.loomytrip.backend.entity.PlanningSession;
import com.loomytrip.backend.entity.Trip;
import org.springframework.stereotype.Component;

@Component
public class EntityMapper {

    public TripSummaryResponse toTripSummary(Trip trip) {
        return new TripSummaryResponse(
                trip.getId(),
                trip.getTripName(),
                trip.getStartDate(),
                trip.getDurationDays(),
                trip.getUpdatedAt()
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
