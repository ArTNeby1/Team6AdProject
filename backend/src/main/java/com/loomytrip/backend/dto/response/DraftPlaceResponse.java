package com.loomytrip.backend.dto.response;

import com.loomytrip.backend.entity.ValidationStatus;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

public record DraftPlaceResponse(
        Long id,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String category,
        ValidationStatus validationStatus,
        String note,
        Integer suggestedDay,
        LocalTime startTime,
        List<DraftActivityResponse> activities
) {
}
