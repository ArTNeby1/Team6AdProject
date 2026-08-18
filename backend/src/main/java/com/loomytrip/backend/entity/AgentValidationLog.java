package com.loomytrip.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Admin-only audit trail for the data exchanged with the planning LLM.
 */
@Getter
@Setter
@Entity
@Table(name = "agent_validation_log")
public class AgentValidationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "planning_session_id")
    private PlanningSession planningSession;

    @Column(nullable = false, length = 32)
    private String operation;

    @Column(name = "request_payload", nullable = false, columnDefinition = "LONGTEXT")
    private String requestPayload;

    @Column(name = "response_payload", nullable = false, columnDefinition = "LONGTEXT")
    private String responsePayload;

    @Column(nullable = false, length = 16)
    private String outcome;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
