package com.loomytrip.backend.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Day-level route summary for map / navigation UIs (F-14).
 */
public record TripRouteResponse(
        Long tripId,
        Integer day,
        Integer stopCount,
        BigDecimal totalDistanceKm,
        Integer totalDurationMinutes,
        String googleMapsUrl,
        List<RouteLegResponse> legs,
        List<TripTransportResponse> transports,
        List<String> warnings
) {
    public record RouteLegResponse(
            Long fromScheduleId,
            Long toScheduleId,
            String fromName,
            String toName,
            BigDecimal distanceKm,
            Integer durationMinutes,
            String googleMapLink
    ) {
    }
}
