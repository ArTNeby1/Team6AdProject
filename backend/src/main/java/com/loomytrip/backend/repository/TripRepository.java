package com.loomytrip.backend.repository;

import com.loomytrip.backend.entity.Trip;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRepository extends JpaRepository<Trip, Long> {
    List<Trip> findByUser_IdOrderByUpdatedAtDesc(Long userId);

    Optional<Trip> findByShareToken(String shareToken);

    List<Trip> findByCreatedAtBetween(Instant from, Instant to);
}
