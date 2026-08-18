package com.loomytrip.backend.repository;

import com.loomytrip.backend.entity.PlanningSession;
import java.util.Collection;
import java.util.List;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanningSessionRepository extends JpaRepository<PlanningSession, Long> {
    List<PlanningSession> findByUser_IdOrderByUpdatedAtDesc(Long userId);

    List<PlanningSession> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant fromInclusive, Instant toExclusive);

    List<PlanningSession> findByUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(Instant fromInclusive, Instant toExclusive);

    List<PlanningSession> findByConfirmedTrip_IdIn(Collection<Long> tripIds);
}
