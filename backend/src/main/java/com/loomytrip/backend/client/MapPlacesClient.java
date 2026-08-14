package com.loomytrip.backend.client;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Contract for Google Places / OSM geocoding validation (F-05).
 */
public interface MapPlacesClient {

    Optional<PlaceMatch> validatePlace(String name, String address);

    /**
     * True if {@code name} is a notable place within Singapore — used to check a free-text
     * "destination" string (see PlanningService#outOfScopeDestination), not to resolve an
     * itinerary stop's coordinates. Deliberately stricter than {@link #validatePlace}: a
     * plain Singapore-scoped search for something like "Tokyo" still matches a handful of
     * local shops/restaurants that happen to have "Tokyo" in their name (real example:
     * "Tokyo Soba" in Tanjong Pagar) — fine for finding a specific stop, wrong for deciding
     * whether an entire trip is actually in Singapore. This filters those out by importance
     * score, which cleanly separates real landmarks/neighbourhoods (Gardens by the Bay,
     * Chinatown — 0.3+) from incidental name collisions (~0.0001).
     */
    boolean existsNotablyInSingapore(String name);

    record PlaceMatch(
            String name,
            String address,
            BigDecimal latitude,
            BigDecimal longitude,
            String externalPlaceId
    ) {
    }
}
