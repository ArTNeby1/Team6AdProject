package com.loomytrip.backend.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record RecommendationItemResponse(
        Long id,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String category,
        Double distanceKm,
        String reason,
        List<String> activities
) {
}
