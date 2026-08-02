package com.loomytrip.backend.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateImportRequest(
        @NotBlank String sourceType,
        String title,
        String rawContent,
        String sourceUrl
) {
}
