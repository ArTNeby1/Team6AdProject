package com.loomytrip.backend.controller;

import com.loomytrip.backend.dto.request.AddTripScheduleRequest;
import com.loomytrip.backend.dto.request.BulkUpdateSchedulesRequest;
import com.loomytrip.backend.dto.request.CreateTripRequest;
import com.loomytrip.backend.dto.request.UpdateTripRequest;
import com.loomytrip.backend.dto.response.TripSummaryResponse;
import com.loomytrip.backend.service.TripService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @GetMapping
    public List<TripSummaryResponse> listTrips() {
        return tripService.listMyTrips();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TripSummaryResponse createTrip(@Valid @RequestBody CreateTripRequest request) {
        return tripService.createTrip(request);
    }

    @GetMapping("/{tripId}")
    public TripSummaryResponse getTrip(@PathVariable Long tripId) {
        return tripService.getTrip(tripId);
    }

    @PutMapping("/{tripId}")
    public TripSummaryResponse updateTrip(@PathVariable Long tripId, @RequestBody UpdateTripRequest request) {
        return tripService.updateTrip(tripId, request);
    }

    @PostMapping("/{tripId}/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    public TripSummaryResponse addSchedules(@PathVariable Long tripId, @Valid @RequestBody AddTripScheduleRequest request) {
        return tripService.addSchedules(tripId, request);
    }

    @PutMapping("/{tripId}/schedules/bulk")
    public TripSummaryResponse bulkUpdateSchedules(@PathVariable Long tripId, @Valid @RequestBody BulkUpdateSchedulesRequest request) {
        return tripService.bulkUpdateSchedules(tripId, request);
    }

    @PostMapping("/{tripId}/generate")
    public Object generateItinerary(@PathVariable Long tripId) {
        return tripService.generateItinerary(tripId);
    }
}
