package com.loomytrip.backend.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record TripDashboardResponse(
        Long tripId,
        Integer totalPlaces,
        Integer uniqueDestinations,
        List<CategoryCount> categoryCounts,
        BigDecimal totalDistanceKm,
        Integer totalTravelDurationMinutes,
        boolean metricsComplete,
        Integer daysWithRoute,
        List<String> warnings
) {
    public record CategoryCount(String category, long count) {
    }
}
