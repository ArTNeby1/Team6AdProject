package com.loomytrip.backend.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiPlanItineraryResult(
        String status,
        List<PlannedDay> days
) {
    public AiPlanItineraryResult {
        days = days == null ? List.of() : days;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlannedDay(
            Integer day,
            String date,
            @JsonProperty("weather_summary") String weatherSummary,
            List<PlannedStop> stops
    ) {
        public PlannedDay {
            stops = stops == null ? List.of() : stops;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlannedStop(
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
}
