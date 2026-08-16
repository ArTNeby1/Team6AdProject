package com.loomytrip.backend.client;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Contract for Directions / Routes APIs (F-14).
 */
public interface RoutingClient {

    /** Matches Google Routes API's `travelMode` enum (DRIVE/WALK/BICYCLE/TRANSIT) — see
     * https://developers.google.com/maps/documentation/routes/reference/rest/v2/TopLevel/computeRoutes#TravelMode.
     * {@code label} is what gets stored in {@code trip_transport.transport_type} and shown
     * in the frontend's "X min driving" tags (F-14 screenshot, 2026-08-16). */
    enum TransportMode {
        DRIVING("driving"),
        WALKING("walking"),
        TRANSIT("transit"),
        BICYCLING("bicycling");

        private final String label;

        TransportMode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    Optional<RouteEstimate> estimate(
            BigDecimal fromLat, BigDecimal fromLng, BigDecimal toLat, BigDecimal toLng, TransportMode mode);

    record RouteEstimate(
            Integer durationMinutes,
            BigDecimal distanceKm,
            String googleMapLink
    ) {
    }
}
