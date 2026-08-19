package com.loomytrip.backend.dto.response;

import java.util.List;

/**
 * The /admin/eval payload: the four metrics averaged across all scored imports, plus the
 * per-import breakdown behind them. {@code scoredCount} is how many imports the averages are
 * over (records whose ML judge succeeded); {@code totalCount} is how many imports were looked
 * at in total (bounded by the endpoint's cap).
 */
public record ExtractionEvaluationSummaryResponse(
        int totalCount,
        int scoredCount,
        Averages averages,
        List<ExtractionEvaluationResponse> records
) {
    public record Averages(
            Double precision,
            Double recall,
            Double f1,
            Double groundedness
    ) {
    }
}
