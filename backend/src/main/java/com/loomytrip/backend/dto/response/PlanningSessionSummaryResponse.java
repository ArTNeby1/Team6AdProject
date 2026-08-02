package com.loomytrip.backend.dto.response;

import com.loomytrip.backend.entity.PlanningSessionStatus;
import java.time.Instant;

public record PlanningSessionSummaryResponse(
        Long id,
        String title,
        String initialBrief,
        PlanningSessionStatus status,
        Long confirmedTripId,
        Instant updatedAt
) {
}
