package com.loomytrip.backend.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AddTripScheduleRequest(
        @NotNull @Min(1) Integer day,
        @NotEmpty List<String> locationNames
) {
}
