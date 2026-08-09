package com.loomytrip.backend.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Result screen payload (ML/docs/ai_contract.md section 2.4): the newly created Trip
 * (`id` at the top level, same shape frontend already reads off `confirm`'s response),
 * plus the AI's weather summary and nearby suggestions it did not persist.
 */
public record ConfirmSessionResponse(
        Long id,
        String tripName,
        LocalDate startDate,
        Integer durationDays,
        Instant updatedAt,
        String weatherSummary,
        List<SuggestedAdditionResponse> suggestedAdditions
) {
}
