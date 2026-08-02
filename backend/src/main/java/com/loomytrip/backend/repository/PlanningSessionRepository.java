package com.loomytrip.backend.repository;

import com.loomytrip.backend.entity.PlanningSession;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanningSessionRepository extends JpaRepository<PlanningSession, Long> {
    List<PlanningSession> findByUser_IdOrderByUpdatedAtDesc(Long userId);
}
