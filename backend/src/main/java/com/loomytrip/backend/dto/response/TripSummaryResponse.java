package com.loomytrip.backend.dto.response;

import java.time.Instant;
import java.time.LocalDate;

public record TripSummaryResponse(
        Long id,
        String tripName,
        LocalDate startDate,
        Integer durationDays,
        Instant updatedAt
) {
}
