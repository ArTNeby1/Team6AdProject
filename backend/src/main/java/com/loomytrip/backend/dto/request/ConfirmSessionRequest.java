package com.loomytrip.backend.dto.request;

/**
 * Body for {@code POST /planning-sessions/{id}/confirm}. Optional — most sessions don't
 * need it, since the AI usually already picked up the day count during extraction (see
 * {@code PlanningSessionDetailResponse#durationDays}). Only send {@code durationDays} here
 * when that field came back null (the user's text never said how many days) and the
 * frontend prompted for it directly. Backend precedence: this value, if present, always
 * wins over whatever the AI guessed — the user's explicit answer beats an inference.
 */
public record ConfirmSessionRequest(Integer durationDays) {
}
