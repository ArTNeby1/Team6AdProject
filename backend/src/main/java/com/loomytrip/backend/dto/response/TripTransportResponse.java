package com.loomytrip.backend.dto.response;

import java.math.BigDecimal;

public record TripTransportResponse(
        Long id,
        Long prevScheduleId,
        Long nextScheduleId,
        String fromName,
        String toName,
        String transportType,
        BigDecimal distanceKm,
        Integer durationMinutes,
        String googleMapLink,
        String routeDesc
) {
}
