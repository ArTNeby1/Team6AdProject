package com.loomytrip.backend.repository;

import com.loomytrip.backend.entity.Destination;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DestinationRepository extends JpaRepository<Destination, Long> {
    List<Destination> findByNameContainingIgnoreCase(String name);
    Optional<Destination> findByExternalPlaceId(String externalPlaceId);
}
