package com.loomytrip.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "planning_session")
public class PlanningSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 255)
    private String title;

    @Column(name = "initial_brief", columnDefinition = "TEXT")
    private String initialBrief;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PlanningSessionStatus status = PlanningSessionStatus.ACTIVE;

    /** How many days the AI thinks the user wants, parsed from free text (e.g. "a 3-day
     * trip") — see PlanningService#persistExtraction's read of the extraction result's
     * {@code duration_days} key. Null means the user never said; the frontend is expected
     * to ask and pass it explicitly as {@code ConfirmSessionRequest#durationDays} instead
     * (see PlanningService#confirmSession). */
    @Column(name = "duration_days")
    private Integer durationDays;

    /** First calendar date explicitly stated in the imported notes. Null means the user
     * did not provide a date, so confirmation keeps the existing today fallback. */
    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    /** Stable machine-readable counterpart to {@link #failureReason} (which is a
     * human-readable sentence) — e.g. {@code NO_USEFUL_CONTENT}, {@code OUT_OF_SCOPE},
     * {@code IMPORT_FAILED} — so the frontend can branch on a fixed value instead of
     * string-matching English prose. See PlanningService#safeImportFailureCode. */
    @Column(name = "failure_code", length = 32)
    private String failureCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_trip_id")
    private Trip confirmedTrip;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
