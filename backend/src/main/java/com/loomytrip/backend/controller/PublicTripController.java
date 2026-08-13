package com.loomytrip.backend.controller;

import com.loomytrip.backend.dto.response.TripSummaryResponse;
import com.loomytrip.backend.service.TripService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated read-only access for trips someone has shared (see
 * TripController#shareTrip / TripService#getSharedTrip). Kept as its own controller/base
 * path — rather than a public-friendly branch inside TripController — so the "no auth
 * required here" boundary in SecurityConfig is a single, obvious path prefix
 * ({@code /api/v1/public/**}) instead of scattered per-method exceptions.
 */
@RestController
@RequestMapping("/api/v1/public/trips")
public class PublicTripController {

    private final TripService tripService;

    public PublicTripController(TripService tripService) {
        this.tripService = tripService;
    }

    @GetMapping("/{shareToken}")
    public TripSummaryResponse getSharedTrip(@PathVariable String shareToken) {
        return tripService.getSharedTrip(shareToken);
    }
}
