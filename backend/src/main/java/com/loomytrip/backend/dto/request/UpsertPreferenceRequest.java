package com.loomytrip.backend.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpsertPreferenceRequest(
        @NotBlank String preferenceKey,
        @NotBlank String preferenceValue
) {
}
