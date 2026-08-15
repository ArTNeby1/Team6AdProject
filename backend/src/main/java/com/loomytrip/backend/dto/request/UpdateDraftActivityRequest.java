package com.loomytrip.backend.dto.request;

import java.time.LocalTime;

/** Drag-and-drop reorder in the import review UI — only non-null fields are applied. */
public record UpdateDraftActivityRequest(
        Integer suggestedDay,
        LocalTime startTime
) {
}
