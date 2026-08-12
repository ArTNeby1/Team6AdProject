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
 * Geocodes draft places via OpenStreetMap Nominatim (no API key required for course/dev use).
 * Failures degrade to {@link Optional#empty()} so planning flows stay usable offline.
 */
@Component
public class MapPlacesClientHttp implements MapPlacesClient {

    private final RestClient restClient;

    public MapPlacesClientHttp(
            @Value("${loomytrip.map.nominatim-base-url:https://nominatim.openstreetmap.org}") String baseUrl
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(15).toMillis());
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", "LoomyTripBackend/1.0 (NUS AD project; contact=loomytrip@local)")
                .defaultHeader("Accept", "application/json")
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<PlaceMatch> validatePlace(String name, String address) {
        String query = buildQuery(name, address);
        if (query.isBlank()) {
            return Optional.empty();
        }

        try {
            List<Map<String, Object>> results = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", query)
                            .queryParam("format", "json")
                            .queryParam("limit", "3")
                            .queryParam("addressdetails", "0")
                            .build())
                    .retrieve()
                    .body(List.class);

            if (results == null || results.isEmpty()) {
                return Optional.empty();
            }

            Map<String, Object> best = results.get(0);
            BigDecimal lat = toDecimal(best.get("lat"));
            BigDecimal lon = toDecimal(best.get("lon"));
            if (lat == null || lon == null) {
                return Optional.empty();
            }

            String displayName = stringVal(best.get("display_name"));
            String placeId = stringVal(best.get("place_id"));
            return Optional.of(new PlaceMatch(
                    name != null && !name.isBlank() ? name.trim() : displayName,
                    displayName,
                    lat,
                    lon,
                    placeId
            ));
        } catch (RestClientException | ClassCastException e) {
            return Optional.empty();
        }
    }

    private static String buildQuery(String name, String address) {
        String n = name == null ? "" : name.trim();
        String a = address == null ? "" : address.trim();
        if (!n.isEmpty() && !a.isEmpty() && !a.equalsIgnoreCase(n)) {
            return n + ", " + a;
        }
        return !n.isEmpty() ? n : a;
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.toString()).setScale(7, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String stringVal(Object value) {
        return value == null ? null : value.toString();
    }
}
