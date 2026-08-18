package com.loomytrip.backend.dto.request;

import java.math.BigDecimal;
import java.time.LocalTime;

public record UpdateDraftPlaceRequest(
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String category,
        String note,
        Integer suggestedDay,
        LocalTime startTime
) {
}
