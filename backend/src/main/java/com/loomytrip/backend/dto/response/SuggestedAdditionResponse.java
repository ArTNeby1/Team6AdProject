package com.loomytrip.backend.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record SuggestedAdditionResponse(
        String name,
        String type,
        BigDecimal latitude,
        BigDecimal longitude,
        Double distanceKm,
        String reason,
        List<String> activities
) {
}
