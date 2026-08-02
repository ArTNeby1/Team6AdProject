package com.loomytrip.backend.dto.response;

import java.math.BigDecimal;

public record DestinationResponse(
        Long id,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String category
) {
}
