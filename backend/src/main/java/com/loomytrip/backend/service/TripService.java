package com.loomytrip.backend.service;

import com.loomytrip.backend.dto.request.CreateTripRequest;
import com.loomytrip.backend.dto.response.TripSummaryResponse;
import com.loomytrip.backend.entity.Trip;
import com.loomytrip.backend.entity.TripDay;
import com.loomytrip.backend.entity.TripPreference;
import com.loomytrip.backend.entity.User;
import com.loomytrip.backend.exception.ApiException;
import com.loomytrip.backend.mapper.EntityMapper;
import com.loomytrip.backend.repository.TripDayRepository;
import com.loomytrip.backend.repository.TripPreferenceRepository;
import com.loomytrip.backend.repository.TripRepository;
import com.loomytrip.backend.repository.UserRepository;
import com.loomytrip.backend.util.SecurityUtils;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripService {

    private final TripRepository tripRepository;
    private final TripDayRepository tripDayRepository;
    private final TripPreferenceRepository tripPreferenceRepository;
    private final UserRepository userRepository;
    private final EntityMapper entityMapper;

    public TripService(
            TripRepository tripRepository,
            TripDayRepository tripDayRepository,
            TripPreferenceRepository tripPreferenceRepository,
            UserRepository userRepository,
            EntityMapper entityMapper
    ) {
        this.tripRepository = tripRepository;
        this.tripDayRepository = tripDayRepository;
        this.tripPreferenceRepository = tripPreferenceRepository;
        this.userRepository = userRepository;
        this.entityMapper = entityMapper;
    }

    @Transactional(readOnly = true)
    public List<TripSummaryResponse> listMyTrips() {
        User user = currentUser();
        return tripRepository.findByUser_IdOrderByUpdatedAtDesc(user.getId()).stream()
                .map(entityMapper::toTripSummary)
                .toList();
    }

    @Transactional
    public TripSummaryResponse createTrip(CreateTripRequest request) {
        User user = currentUser();

        Trip trip = new Trip();
        trip.setUser(user);
        trip.setTripName(request.tripName());
        trip.setStartDate(request.startDate());
        trip.setDurationDays(request.durationDays());
        Trip saved = tripRepository.save(trip);

        List<TripDay> days = new ArrayList<>();
        for (int i = 1; i <= request.durationDays(); i++) {
            TripDay day = new TripDay();
            day.setTrip(saved);
            day.setDaySequence(i);
            days.add(day);
        }
        tripDayRepository.saveAll(days);

        if (request.travelStyle() != null || request.preferTransport() != null) {
            TripPreference preference = new TripPreference();
            preference.setTrip(saved);
            preference.setTravelStyle(request.travelStyle());
            preference.setPreferTransport(request.preferTransport());
            tripPreferenceRepository.save(preference);
        }

        return entityMapper.toTripSummary(saved);
    }

    @Transactional(readOnly = true)
    public TripSummaryResponse getTrip(Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND", "Trip not found"));
        ensureOwner(trip);
        return entityMapper.toTripSummary(trip);
    }

    /**
     * Placeholder for F-09 AI itinerary generation.
     */
    public Object generateItinerary(Long tripId) {
        getTrip(tripId);
        throw new ApiException(
                HttpStatus.NOT_IMPLEMENTED,
                "NOT_IMPLEMENTED",
                "Itinerary generation will call AiPlanningClient in a later iteration"
        );
    }

    private User currentUser() {
        return userRepository.findByEmail(SecurityUtils.currentUserEmail())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "User not found"));
    }

    private void ensureOwner(Trip trip) {
        User user = currentUser();
        if (!trip.getUser().getId().equals(user.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Trip does not belong to current user");
        }
    }
}
