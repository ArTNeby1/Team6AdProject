package com.loomytrip.backend.dto.response;

import java.time.Instant;

public record AgentValidationLogResponse(
        Long id,
        Long userId,
        String userEmail,
        Long planningSessionId,
        String operation,
        String requestPayload,
        String responsePayload,
        String outcome,
        Instant createdAt
) {
}
