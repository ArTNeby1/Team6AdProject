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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "draft_place")
public class DraftPlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private PlanningSession session;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 255)
    private String address;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(length = 64)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false, length = 32)
    private ValidationStatus validationStatus = ValidationStatus.UNVALIDATED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_id")
    private Destination destination;

    @Column(length = 255)
    private String note;

    /** Drag-and-drop day/time assignment at the *place* level — see DraftActivity's
     * matching columns for the original (activity-level) version. Needed because a place
     * frequently has zero activities (AI extraction often returns an empty list), so
     * relying only on DraftActivity.suggestedDay leaves those places with nowhere to
     * record which day they belong to. confirmSession() checks this field first, falling
     * back to any activity's suggestedDay for places set up before this column existed. */
    @Column(name = "suggested_day")
    private Integer suggestedDay;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
