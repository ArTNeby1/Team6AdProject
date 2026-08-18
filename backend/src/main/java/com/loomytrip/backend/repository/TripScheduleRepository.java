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

    /**
     * Every destination name the user has ever scheduled, across all of their trips
     * (not just the current one) — used to keep the AI recommender from re-suggesting
     * a place the traveler has already planned/visited on a different trip.
     */
    @Query("""
            select distinct lower(schedule.destination.name)
            from TripSchedule schedule
            where schedule.tripDay.trip.user.id = :userId
            """)
    Set<String> findVisitedDestinationNamesByUserId(Long userId);
}
