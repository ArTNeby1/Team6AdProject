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
            String startTime,
            /** Maps to {@code trip_schedule.is_locked} (pre-existing column, previously
             * never read or written anywhere — see its own doc comment: "AI should avoid
             * moving"). Null leaves the existing flag untouched. EditPage.jsx sets this true
             * the moment the user hand-types a start_time, so a drag-and-drop reorder's
             * auto-cascaded times (2026-08-16) skip over it instead of overwriting a time
             * the user picked on purpose. */
            Boolean locked
    ) {
    }
}
