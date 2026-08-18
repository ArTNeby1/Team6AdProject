package com.loomytrip.backend.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/**
 * Response shape of the Python AI service's `POST /recommend`
 * (see ML/app/main.py and ML/docs/ai_contract.md section 5).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiRecommendResult(
        String status,
        @JsonProperty("weather_summary") String weatherSummary,
        @JsonProperty("ordered_stops") List<OrderedStop> orderedStops,
        @JsonProperty("suggested_additions") List<SuggestedAddition> suggestedAdditions
) {

    public AiRecommendResult {
        orderedStops = orderedStops == null ? List.of() : orderedStops;
        suggestedAdditions = suggestedAdditions == null ? List.of() : suggestedAdditions;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderedStop(
            String name,
            String type,
            BigDecimal lat,
            BigDecimal lng,
            List<String> activities,
            Integer order,
            @JsonProperty("time_of_day") String timeOfDay,
            @JsonProperty("is_outdoor") Boolean outdoor,
            String reason
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SuggestedAddition(
            String name,
            String type,
            BigDecimal lat,
            BigDecimal lng,
            @JsonProperty("distance_km") Double distanceKm,
            Double similarity,
            String reason,
            List<String> activities
    ) {
    }
}
