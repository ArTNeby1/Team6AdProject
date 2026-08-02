package com.loomytrip.backend.repository;

import com.loomytrip.backend.entity.TripSchedule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripScheduleRepository extends JpaRepository<TripSchedule, Long> {
    List<TripSchedule> findByTripDay_IdOrderBySequenceAsc(Long tripDayId);
}
