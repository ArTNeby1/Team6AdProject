package com.loomytrip.backend.repository;

import com.loomytrip.backend.entity.TripDay;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripDayRepository extends JpaRepository<TripDay, Long> {
    List<TripDay> findByTrip_IdOrderByDaySequenceAsc(Long tripId);
    Optional<TripDay> findByTrip_IdAndDaySequence(Long tripId, Integer daySequence);
    void deleteByTrip_IdAndDaySequenceGreaterThan(Long tripId, Integer daySequence);
}
