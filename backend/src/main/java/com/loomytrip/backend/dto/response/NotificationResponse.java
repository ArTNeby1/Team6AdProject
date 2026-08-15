package com.loomytrip.backend.dto.response;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        String type,
        String title,
        String body,
        Long planningSessionId,
        Long tripId,
        Instant readAt,
        Instant createdAt
) {
}
