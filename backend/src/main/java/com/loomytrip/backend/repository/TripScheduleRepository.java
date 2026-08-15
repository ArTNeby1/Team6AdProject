package com.loomytrip.backend.repository;

import com.loomytrip.backend.entity.TripSchedule;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TripScheduleRepository extends JpaRepository<TripSchedule, Long> {
    List<TripSchedule> findByTripDay_IdOrderBySequenceAsc(Long tripDayId);

    List<TripSchedule> findByTripDay_Trip_IdOrderByTripDay_DaySequenceAscSequenceAsc(Long tripId);

    @Query("""
            select schedule from TripSchedule schedule
            join fetch schedule.destination
            join fetch schedule.tripDay day
            join fetch day.trip
            where day.trip.id in :tripIds
            """)
    List<TripSchedule> findAnalyticsSchedulesByTripIds(Set<Long> tripIds);
}
