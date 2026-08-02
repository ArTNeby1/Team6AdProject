package com.loomytrip.backend.dto.response;

import com.loomytrip.backend.entity.ImportStatus;
import java.time.Instant;

public record ImportSummaryResponse(
        Long id,
        String sourceType,
        String title,
        ImportStatus status,
        Instant createdAt
) {
}
