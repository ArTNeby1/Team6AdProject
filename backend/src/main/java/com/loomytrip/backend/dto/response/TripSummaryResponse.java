package com.loomytrip.backend.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TripSummaryResponse(
        Long id,
        String tripName,
        LocalDate startDate,
        Integer durationDays,
        Instant updatedAt,
        /** Derived from today vs. startDate/startDate+durationDays — not a stored column
         * (no explicit "mark as finished" support; see NOT_STARTED/ACTIVE/FINISHED). */
        String status,
        /** From `trip_preference` — null if the trip has none set. */
        String travelStyle,
        String preferTransport,
        boolean favorite,
        List<TripScheduleResponse> schedules
) {
}
