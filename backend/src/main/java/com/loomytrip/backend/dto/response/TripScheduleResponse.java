package com.loomytrip.backend.dto.response;

import java.time.LocalTime;

/**
 * A single stop on a trip day. Nests {@code tripDay} (rather than a flat
 * {@code daySequence}) to match what the frontend already reads off
 * `GET /trips` — see Frontend_Web/src/context/TripContext.jsx `fetchTrips()`.
 */
public record TripScheduleResponse(
        Long id,
        DestinationResponse destination,
        TripDayInfo tripDay,
        Integer sequence,
        LocalTime startTime,
        LocalTime endTime,
        Integer plannedDurationMinutes,
        String note
) {
    public record TripDayInfo(Long id, Integer daySequence) {
    }
}
