package com.loomytrip.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;

/**
 * Partial update — only non-null fields are applied. {@code @JsonIgnoreProperties} so
 * fields the frontend already sends but the backend doesn't support yet (e.g.
 * {@code coverImage} — no column for it, see ML/docs handoff notes) are silently dropped
 * instead of failing the whole request.
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
