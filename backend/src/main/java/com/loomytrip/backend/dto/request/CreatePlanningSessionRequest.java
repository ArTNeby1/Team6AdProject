package com.loomytrip.backend.dto.request;

public record CreatePlanningSessionRequest(
        String title,
        String initialBrief
) {
}
