package com.loomytrip.backend.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Route-leg estimates via Google's Routes API ({@code computeRoutes}), one call per
 * {@link RoutingClient.TransportMode} — Google only ever returns a single mode per request,
 * unlike OSRM which only ever served driving. Falls back to a Haversine straight-line
 * estimate whenever {@code loomytrip.routing.google-maps-api-key} is unset (local dev
 * without a key configured) or the API call fails for any reason — same resilience pattern
 * as {@link com.loomytrip.backend.client.MapPlacesClientHttp}: a broken/rate-limited/
 * unconfigured external geo service degrades the numbers, it never fails the request.
 */
@Component
public class RoutingClientHttp implements RoutingClient {

    private static final double EARTH_RADIUS_KM = 6371.0;
    /** Assumed average speed when falling back to straight-line distance, per mode —
     * driving/transit share a road-network multiplier, walking/cycling are much slower. */
    private static final Map<TransportMode, Double> FALLBACK_SPEED_KMH = Map.of(
            TransportMode.DRIVING, 25.0,
            TransportMode.TRANSIT, 20.0,
            TransportMode.BICYCLING, 15.0,
            TransportMode.WALKING, 5.0
    );

    private final RestClient restClient;
    private final String apiKey;

    public RoutingClientHttp(
            @Value("${loomytrip.routing.google-maps-api-key:}") String apiKey,
            @Value("${loomytrip.routing.google-maps-base-url:https://routes.googleapis.com}") String baseUrl
    ) {
        this.apiKey = apiKey;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(15).toMillis());
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<RouteEstimate> estimate(
            BigDecimal fromLat,
            BigDecimal fromLng,
            BigDecimal toLat,
            BigDecimal toLng,
            TransportMode mode
    ) {
        if (fromLat == null || fromLng == null || toLat == null || toLng == null) {
            return Optional.empty();
        }

        String googleLink = googleMapsLink(fromLat, fromLng, toLat, toLng, mode);

        if (apiKey == null || apiKey.isBlank()) {
            return Optional.of(haversineEstimate(fromLat, fromLng, toLat, toLng, mode, googleLink));
        }

        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("origin", latLng(fromLat, fromLng));
            requestBody.put("destination", latLng(toLat, toLng));
            requestBody.put("travelMode", googleTravelMode(mode));
            // routingPreference (traffic-aware ETAs) is only valid for DRIVE/TWO_WHEELER —
            // the API rejects the request outright if it's sent alongside WALK/BICYCLE/TRANSIT.
            if (mode == TransportMode.DRIVING) {
                requestBody.put("routingPreference", "TRAFFIC_AWARE");
            }

            Map<String, Object> body = restClient.post()
                    .uri("/directions/v2:computeRoutes")
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", "routes.duration,routes.distanceMeters")
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            List<Map<String, Object>> routes = body == null ? null : (List<Map<String, Object>>) body.get("routes");
            if (routes == null || routes.isEmpty()) {
                return Optional.of(haversineEstimate(fromLat, fromLng, toLat, toLng, mode, googleLink));
            }

            Map<String, Object> route = routes.get(0);
            Integer durationSeconds = parseGoogleDurationSeconds(String.valueOf(route.get("duration")));
            Object distanceMetersValue = route.get("distanceMeters");
            if (durationSeconds == null || distanceMetersValue == null) {
                return Optional.of(haversineEstimate(fromLat, fromLng, toLat, toLng, mode, googleLink));
            }

            double distanceMeters = ((Number) distanceMetersValue).doubleValue();
            BigDecimal distanceKm = BigDecimal.valueOf(distanceMeters / 1000.0).setScale(2, RoundingMode.HALF_UP);
            int durationMinutes = Math.max(1, (int) Math.round(durationSeconds / 60.0));
            return Optional.of(new RouteEstimate(durationMinutes, distanceKm, googleLink, false));
        } catch (RestClientException | ClassCastException | NullPointerException e) {
            return Optional.of(haversineEstimate(fromLat, fromLng, toLat, toLng, mode, googleLink));
        }
    }

    private static Map<String, Object> latLng(BigDecimal lat, BigDecimal lng) {
        return Map.of("location", Map.of("latLng", Map.of("latitude", lat, "longitude", lng)));
    }

    private static String googleTravelMode(TransportMode mode) {
        return switch (mode) {
            case DRIVING -> "DRIVE";
            case WALKING -> "WALK";
            case BICYCLING -> "BICYCLE";
            case TRANSIT -> "TRANSIT";
        };
    }

    /** Google's Duration proto serializes as a string like {@code "463s"} — everything
     * before the trailing "s" is whole seconds. Null if the shape doesn't match. */
    private static Integer parseGoogleDurationSeconds(String raw) {
        if (raw == null || !raw.endsWith("s")) {
            return null;
        }
        try {
            return (int) Double.parseDouble(raw.substring(0, raw.length() - 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static RouteEstimate haversineEstimate(
            BigDecimal fromLat,
            BigDecimal fromLng,
            BigDecimal toLat,
            BigDecimal toLng,
            TransportMode mode,
            String googleLink
    ) {
        double distanceKm = haversineKm(
                fromLat.doubleValue(), fromLng.doubleValue(),
                toLat.doubleValue(), toLng.doubleValue()
        );
        double speedKmh = FALLBACK_SPEED_KMH.get(mode);
        int durationMinutes = Math.max(1, (int) Math.round((distanceKm / speedKmh) * 60.0));
        return new RouteEstimate(
                durationMinutes,
                BigDecimal.valueOf(distanceKm).setScale(2, RoundingMode.HALF_UP),
                googleLink,
                true
        );
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    private static String googleMapsLink(
            BigDecimal fromLat,
            BigDecimal fromLng,
            BigDecimal toLat,
            BigDecimal toLng,
            TransportMode mode
    ) {
        return "https://www.google.com/maps/dir/?api=1"
                + "&origin=" + fromLat + "," + fromLng
                + "&destination=" + toLat + "," + toLng
                + "&travelmode=" + mode.label();
    }
}
