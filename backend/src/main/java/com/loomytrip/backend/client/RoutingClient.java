package com.loomytrip.backend.client;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Contract for Directions / Routes APIs (F-14).
 */
public interface RoutingClient {

    Optional<RouteEstimate> estimate(BigDecimal fromLat, BigDecimal fromLng, BigDecimal toLat, BigDecimal toLng);

    record RouteEstimate(
            Integer durationMinutes,
            BigDecimal distanceKm,
            String googleMapLink
    ) {
    }
}
