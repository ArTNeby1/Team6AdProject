package com.loomytrip.backend.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateTripRequest(
        @NotBlank String tripName,
        @NotNull LocalDate startDate,
        @NotNull @Min(1) Integer durationDays,
        String travelStyle,
        String preferTransport
) {
}
