package com.loomytrip.backend.dto.response;

import java.util.List;

public record GenerateItineraryResponse(
        Long tripId,
        String status,
        List<PlannedDayResponse> days
) {
    public record PlannedDayResponse(
            Integer day,
            String date,
            String weatherSummary,
            List<PlannedStopResponse> stops
    ) {
    }

    public record PlannedStopResponse(
            Long scheduleId,
            String name,
            Integer order,
            String timeOfDay,
            String reason
    ) {
    }
}
