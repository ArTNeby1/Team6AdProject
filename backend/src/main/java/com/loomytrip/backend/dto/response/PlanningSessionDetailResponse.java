package com.loomytrip.backend.dto.response;

import com.loomytrip.backend.entity.PlanningSessionStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Confirmation-screen payload (ML/docs/ai_contract.md section 2.3): the draft places the
 * AI extracted, plus their place_id / real lat-lng, so the frontend can edit/delete before
 * confirming.
 *
 * <p>{@code durationDays} is null when the AI couldn't tell how many days the user wants
 * (they never said) — the frontend should prompt for it and pass the answer explicitly to
 * {@code POST .../confirm} as {@code ConfirmSessionRequest#durationDays} instead of relying
 * on this field. Non-null means the AI already picked it up from the text; no prompt needed.
 */
public record PlanningSessionDetailResponse(
        Long id,
        String title,
        String initialBrief,
        PlanningSessionStatus status,
        Long confirmedTripId,
        Integer durationDays,
        LocalDate startDate,
        String failureCode,
        String failureReason,
        List<DraftPlaceResponse> draftPlaces,
        Instant updatedAt
) {
}
