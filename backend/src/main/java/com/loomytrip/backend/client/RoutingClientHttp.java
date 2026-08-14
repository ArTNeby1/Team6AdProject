package com.loomytrip.backend.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Driving-route estimates via the public OSRM demo server, with Haversine fallback when
 * the network call fails. Suitable for course demos; production should point
 * {@code loomytrip.routing.osrm-base-url} at a self-hosted or commercial router.
 */
@Component
public class RoutingClientHttp implements RoutingClient {

    private static final double EARTH_RADIUS_KM = 6371.0;
    /** Assumed average road speed when falling back to straight-line distance. */
    private static final double FALLBACK_SPEED_KMH = 25.0;

    private final RestClient restClient;

    public RoutingClientHttp(
            @Value("${loomytrip.routing.osrm-base-url:https://router.project-osrm.org}") String baseUrl
    ) {
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
            BigDecimal toLng
    ) {
        if (fromLat == null || fromLng == null || toLat == null || toLng == null) {
            return Optional.empty();
        }

        String googleLink = googleMapsLink(fromLat, fromLng, toLat, toLng);

        try {
            String coordinates = fromLng + "," + fromLat + ";" + toLng + "," + toLat;
            Map<String, Object> body = restClient.get()
                    .uri("/route/v1/driving/{coords}?overview=false", coordinates)
                    .retrieve()
                    .body(Map.class);

            if (body == null || !"Ok".equals(String.valueOf(body.get("code")))) {
                return Optional.of(haversineEstimate(fromLat, fromLng, toLat, toLng, googleLink));
            }

            List<Map<String, Object>> routes = (List<Map<String, Object>>) body.get("routes");
            if (routes == null || routes.isEmpty()) {
                return Optional.of(haversineEstimate(fromLat, fromLng, toLat, toLng, googleLink));
            }

            Map<String, Object> route = routes.get(0);
            double distanceMeters = ((Number) route.get("distance")).doubleValue();
            double durationSeconds = ((Number) route.get("duration")).doubleValue();
            BigDecimal distanceKm = BigDecimal.valueOf(distanceMeters / 1000.0).setScale(2, RoundingMode.HALF_UP);
            int durationMinutes = Math.max(1, (int) Math.round(durationSeconds / 60.0));
            return Optional.of(new RouteEstimate(durationMinutes, distanceKm, googleLink));
        } catch (RestClientException | ClassCastException | NullPointerException e) {
            return Optional.of(haversineEstimate(fromLat, fromLng, toLat, toLng, googleLink));
        }
    }

    private static RouteEstimate haversineEstimate(
            BigDecimal fromLat,
            BigDecimal fromLng,
            BigDecimal toLat,
            BigDecimal toLng,
            String googleLink
    ) {
        double distanceKm = haversineKm(
                fromLat.doubleValue(), fromLng.doubleValue(),
                toLat.doubleValue(), toLng.doubleValue()
        );
        int durationMinutes = Math.max(1, (int) Math.round((distanceKm / FALLBACK_SPEED_KMH) * 60.0));
        return new RouteEstimate(
                durationMinutes,
                BigDecimal.valueOf(distanceKm).setScale(2, RoundingMode.HALF_UP),
                googleLink
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
            BigDecimal toLng
    ) {
        return "https://www.google.com/maps/dir/?api=1"
                + "&origin=" + fromLat + "," + fromLng
                + "&destination=" + toLat + "," + toLng
                + "&travelmode=driving";
    }
}
