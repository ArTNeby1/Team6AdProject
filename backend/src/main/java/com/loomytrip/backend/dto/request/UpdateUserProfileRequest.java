package com.loomytrip.backend.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
        @NotBlank @Size(max = 64) String username,
        @Min(1) @Max(120) Integer age,
        @NotBlank @Size(max = 32) String gender
) {
}
