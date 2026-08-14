package com.loomytrip.backend.dto.response;

public record CrowdHintResponse(
        int quarter,
        double seasonalIndex,
        String level,
        String note
) {
}
