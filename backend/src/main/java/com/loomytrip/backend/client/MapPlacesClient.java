package com.loomytrip.backend.client;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Contract for Google Places / OSM geocoding validation (F-05).
 */
public interface MapPlacesClient {

    Optional<PlaceMatch> validatePlace(String name, String address);

    record PlaceMatch(
            String name,
            String address,
            BigDecimal latitude,
            BigDecimal longitude,
            String externalPlaceId
    ) {
    }
}
