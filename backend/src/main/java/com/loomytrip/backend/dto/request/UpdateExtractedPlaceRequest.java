package com.loomytrip.backend.dto.request;

import java.math.BigDecimal;

public record UpdateExtractedPlaceRequest(
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String category,
        String note
) {
}
