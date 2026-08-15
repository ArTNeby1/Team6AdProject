package com.loomytrip.backend.repository;

import com.loomytrip.backend.entity.TripTransport;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripTransportRepository extends JpaRepository<TripTransport, Long> {
    List<TripTransport> findByTripDay_IdOrderByIdAsc(Long tripDayId);

    List<TripTransport> findByTripDay_Trip_Id(Long tripId);

    void deleteByTripDay_Id(Long tripDayId);
}
