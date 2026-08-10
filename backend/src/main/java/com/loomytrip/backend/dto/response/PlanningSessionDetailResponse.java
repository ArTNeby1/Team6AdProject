package com.loomytrip.backend.dto.response;

import com.loomytrip.backend.entity.PlanningSessionStatus;
import java.time.Instant;
import java.util.List;

/**
 * Confirmation-screen payload (ML/docs/ai_contract.md section 2.3): the draft places the
 * AI extracted, plus their place_id / real lat-lng, so the frontend can edit/delete before
 * confirming.
 */
public record PlanningSessionDetailResponse(
        Long id,
        String title,
        String initialBrief,
        PlanningSessionStatus status,
        Long confirmedTripId,
        List<DraftPlaceResponse> draftPlaces,
        Instant updatedAt
) {
}
