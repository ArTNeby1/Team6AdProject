package com.loomytrip.backend.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * Content-level accuracy for a single import, as shown on /admin/eval. Metrics are 0..1
 * (render as %); {@code available} is false when the ML judge could not be reached, in which
 * case the numeric fields are null and this record is left out of the averages.
 */
public record ExtractionEvaluationResponse(
        Long id,
        String userEmail,
        String operation,
        Instant createdAt,
        boolean available,
        Double precision,
        Double recall,
        Double f1,
        Double groundedness,
        String sourceText,
        List<String> predictedPlaces,
        List<String> goldPlaces,
        List<String> matched,
        List<String> missed,
        List<String> spurious
) {
}
