package com.loomytrip.backend.dto.response;

import java.time.LocalTime;

public record DraftActivityResponse(
        Long id,
        String title,
        Integer suggestedDay,
        LocalTime startTime
) {
}
