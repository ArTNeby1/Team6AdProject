package com.loomytrip.backend.repository;

import com.loomytrip.backend.entity.TripPreference;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripPreferenceRepository extends JpaRepository<TripPreference, Long> {
    Optional<TripPreference> findByTrip_Id(Long tripId);
}
