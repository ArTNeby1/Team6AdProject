package com.loomytrip.backend.repository;

import com.loomytrip.backend.entity.AgentValidationLog;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentValidationLogRepository extends JpaRepository<AgentValidationLog, Long> {

    /**
     * Successful imports only, newest first — the input set for the admin LLM Evaluation
     * console. REFINE rows and FAILED rows carry no comparable extraction, so they are
     * excluded from scoring. {@link Pageable} caps how many recent imports get scored.
     */
    List<AgentValidationLog> findByOperationAndOutcomeOrderByIdDesc(
            String operation, String outcome, Pageable pageable);
}
