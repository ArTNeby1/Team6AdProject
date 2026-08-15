package com.loomytrip.backend.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Geocodes draft places via Photon (OpenStreetMap).
 *
 * <p>The public Nominatim instance frequently returns empty results from AWS IP ranges,
 * which left every online import stuck at {@code INVALID} and blocked confirm. Photon is
 * used as the primary lookup so ECS deployments can resolve Singapore landmarks.
 */
@Component
public class MapPlacesClientHttp implements MapPlacesClient {

    private final RestClient restClient;

    public MapPlacesClientHttp(
            @Value("${loomytrip.map.photon-base-url:https://photon.komoot.io}") String baseUrl
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
    public Optional<PlaceMatch> validatePlace(String name, String address) {
        String query = buildQuery(name, address);
        if (query.isBlank()) {
            return Optional.empty();
        }

        try {
            Map<?, ?> body = search(query, 5);
            Map<?, ?> best = firstSingaporeFeature(body).orElse(null);
            if (best == null) {
                return Optional.empty();
            }

            BigDecimal[] coords = coordinates(best);
            if (coords == null) {
                return Optional.empty();
            }

            Map<?, ?> properties = asMap(best.get("properties"));
            String displayName = displayName(properties, name);
            String placeId = stringVal(properties == null ? null : properties.get("osm_id"));
            return Optional.of(new PlaceMatch(
                    name != null && !name.isBlank() ? name.trim() : displayName,
                    displayName,
                    coords[0],
                    coords[1],
                    placeId
            ));
        } catch (RestClientException | ClassCastException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean existsNotablyInSingapore(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        try {
            Map<?, ?> body = search(name.trim(), 5);
            return firstSingaporeFeature(body)
                    .map(feature -> {
                        Map<?, ?> properties = asMap(feature.get("properties"));
                        if (properties == null) {
                            return false;
                        }
                        String matchedName = stringVal(properties.get("name"));
                        if (matchedName == null || matchedName.isBlank()) {
                            return false;
                        }
                        String needle = name.trim().toLowerCase(Locale.ROOT);
                        String haystack = matchedName.toLowerCase(Locale.ROOT);
                        return haystack.contains(needle) || needle.contains(haystack);
                    })
                    .orElse(false);
        } catch (RestClientException | ClassCastException e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> search(String query, int limit) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/")
                        .queryParam("q", query)
                        .queryParam("limit", limit)
                        .queryParam("lang", "en")
                        .build())
                .retrieve()
                .body(Map.class);
    }

    private Optional<Map<?, ?>> firstSingaporeFeature(Map<?, ?> body) {
        if (body == null) {
            return Optional.empty();
        }
        Object featuresValue = body.get("features");
        if (!(featuresValue instanceof List<?> features) || features.isEmpty()) {
            return Optional.empty();
        }
        for (Object feature : features) {
            if (!(feature instanceof Map<?, ?> map)) {
                continue;
            }
            if (isSingapore(map)) {
                return Optional.of(map);
            }
        }
        return Optional.empty();
    }

    private boolean isSingapore(Map<?, ?> feature) {
        Map<?, ?> properties = asMap(feature.get("properties"));
        if (properties == null) {
            return false;
        }
        String countryCode = stringVal(properties.get("countrycode"));
        if (countryCode != null && "sg".equalsIgnoreCase(countryCode)) {
            return true;
        }
        String country = stringVal(properties.get("country"));
        return country != null && country.toLowerCase(Locale.ROOT).contains("singapore");
    }

    private static BigDecimal[] coordinates(Map<?, ?> feature) {
        Map<?, ?> geometry = asMap(feature.get("geometry"));
        if (geometry == null) {
            return null;
        }
        Object coordinatesValue = geometry.get("coordinates");
        if (!(coordinatesValue instanceof List<?> coordinates) || coordinates.size() < 2) {
            return null;
        }
        BigDecimal lon = toDecimal(coordinates.get(0));
        BigDecimal lat = toDecimal(coordinates.get(1));
        if (lat == null || lon == null) {
            return null;
        }
        return new BigDecimal[]{lat, lon};
    }

    private static String displayName(Map<?, ?> properties, String fallback) {
        if (properties == null) {
            return fallback;
        }
        String name = stringVal(properties.get("name"));
        String city = stringVal(properties.get("city"));
        String country = stringVal(properties.get("country"));
        StringBuilder display = new StringBuilder();
        if (name != null && !name.isBlank()) {
            display.append(name);
        }
        if (city != null && !city.isBlank()) {
            if (!display.isEmpty()) {
                display.append(", ");
            }
            display.append(city);
        }
        if (country != null && !country.isBlank()) {
            if (!display.isEmpty()) {
                display.append(", ");
            }
            display.append(country);
        }
        return display.isEmpty() ? fallback : display.toString();
    }

    private static String buildQuery(String name, String address) {
        String n = normalizeQuery(name);
        String a = normalizeQuery(address);
        if (!n.isEmpty() && !a.isEmpty() && !a.equalsIgnoreCase(n)) {
            return n + ", " + a;
        }
        return !n.isEmpty() ? n : a;
    }

    /**
     * Strips noise that makes Photon/Nominatim miss real Singapore landmarks
     * (e.g. "Lau Pa Sat hawker centre", "Kampong Glam / Arab Street").
     */
    private static String normalizeQuery(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw
                .replaceAll("[（(][^）)]*[）)]", " ")
                .replace('/', ' ')
                .replaceAll("(?i)\\b(hawker\\s+centre|hawker\\s+center|food\\s+court)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned;
    }

    private static Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : null;
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
