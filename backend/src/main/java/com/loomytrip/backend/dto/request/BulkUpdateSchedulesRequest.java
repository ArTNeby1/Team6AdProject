package com.loomytrip.backend.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Bulk reorder/move for an existing trip's schedules — the shape
 * Frontend_Web/src/context/TripContext.jsx `saveTripEdits()` sends after a drag-and-drop
 * edit on EditPage.jsx. Only moves/reorders schedules that already exist; it doesn't
 * create new ones (see TripController#addSchedules for that).
 */
public record BulkUpdateSchedulesRequest(
        @NotEmpty List<ScheduleUpdate> schedules
) {
    public record ScheduleUpdate(
            @NotNull Long id,
            @NotNull @Min(1) Integer day,
            @NotNull @Min(1) Integer sequence,
            /** Optional "HH:mm" (or "HH:mm:ss"). Null/blank leaves the existing start_time
             * untouched — EditPage.jsx's time input was being edited in local draft state
             * only and never reached this endpoint at all, so saved time changes never stuck. */
            String startTime
    ) {
    }
}
