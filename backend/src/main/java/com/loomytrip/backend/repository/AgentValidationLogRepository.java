package com.loomytrip.backend.repository;

import com.loomytrip.backend.entity.AgentValidationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentValidationLogRepository extends JpaRepository<AgentValidationLog, Long> {
}
