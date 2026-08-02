package com.loomytrip.backend.repository;

import com.loomytrip.backend.entity.Trip;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRepository extends JpaRepository<Trip, Long> {
    List<Trip> findByUser_IdOrderByUpdatedAtDesc(Long userId);
}
