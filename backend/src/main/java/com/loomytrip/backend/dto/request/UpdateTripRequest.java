package com.loomytrip.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;

/**
 * Partial update — only non-null fields are applied. {@code @JsonIgnoreProperties} so
 * unknown fields the frontend might still send are silently dropped instead of failing
 * the whole request, rather than requiring both sides to redeploy in lockstep.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateTripRequest(
        String tripName,
        LocalDate startDate,
        Integer durationDays,
        String travelStyle,
        String preferTransport,
        Boolean favorite
) {
}
