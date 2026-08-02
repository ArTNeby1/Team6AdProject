package com.loomytrip.backend.exception;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        Instant timestamp,
        String code,
        String message,
        List<String> details
) {
}
