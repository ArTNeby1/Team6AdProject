package com.loomytrip.backend.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpClientsTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void mapPlacesClient_returnsSingaporeMatch_andCleansQueryNoise() {
        server.createContext("/api/", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            assertThat(query).contains("bbox=");
            assertThat(query).contains("Lau");
            assertThat(query).contains("Pa");
            assertThat(query).contains("Sat");
            assertThat(query).doesNotContain("hawker");
            String body = """
                    {
                      "features": [
                        {
                          "geometry": { "coordinates": [103.8515, 1.2805] },
                          "properties": {
                            "name": "Lau Pa Sat",
                            "city": "Singapore",
                            "country": "Singapore",
                            "countrycode": "sg",
                            "osm_id": 123
                          }
                        }
                      ]
                    }
                    """;
            writeJson(exchange, 200, body);
        });

        MapPlacesClientHttp client = new MapPlacesClientHttp(baseUrl);
        Optional<MapPlacesClient.PlaceMatch> match =
                client.validatePlace("Lau Pa Sat hawker centre", null);

        assertThat(match).isPresent();
        assertThat(match.get().latitude()).isEqualByComparingTo("1.2805000");
        assertThat(match.get().longitude()).isEqualByComparingTo("103.8515000");
        assertThat(client.existsNotablyInSingapore("Lau Pa Sat")).isTrue();
    }

    @Test
    void mapPlacesClient_ignoresNonSingaporeFeatures_andBlankQuery() {
        server.createContext("/api/", exchange -> writeJson(exchange, 200, """
                {
                  "features": [
                    {
                      "geometry": { "coordinates": [-74.0, 40.7] },
                      "properties": {
                        "name": "Chinatown",
                        "country": "United States",
                        "countrycode": "us"
                      }
                    }
                  ]
                }
                """));

        MapPlacesClientHttp client = new MapPlacesClientHttp(baseUrl);
        assertThat(client.validatePlace("Chinatown", null)).isEmpty();
        assertThat(client.validatePlace("  ", null)).isEmpty();
        assertThat(client.existsNotablyInSingapore(" ")).isFalse();
    }

    @Test
    void mapPlacesClient_degradesToEmptyOnHttpFailure() {
        server.createContext("/api/", exchange -> writeText(exchange, 503, "unavailable"));

        MapPlacesClientHttp client = new MapPlacesClientHttp(baseUrl);

        assertThat(client.validatePlace("Marina Bay Sands", null)).isEmpty();
        assertThat(client.existsNotablyInSingapore("Marina Bay Sands")).isFalse();
    }

    @Test
    void aiPlanningClient_mapsNoUsefulContent_andUnavailableFallback() {
        server.createContext("/extract-travel-info", exchange -> writeText(
                exchange,
                422,
                "{\"detail\":\"NO_USEFUL_CONTENT: nothing useful here\"}"
        ));
        server.createContext("/refine", exchange -> writeText(exchange, 500, "boom"));
        server.createContext("/recommend", exchange -> writeText(exchange, 500, "boom"));
        server.createContext("/plan-itinerary", exchange -> writeText(exchange, 500, "boom"));

        AiPlanningClientHttp client = new AiPlanningClientHttp(baseUrl);

        Map<String, Object> blank = client.extractTravelInfo("  ", null);
        assertThat(blank.get("status")).isEqualTo("NO_CONTENT");

        Map<String, Object> noUseful = client.extractTravelInfo("hello", null);
        assertThat(noUseful.get("status")).isEqualTo("NO_USEFUL_CONTENT");
        assertThat(String.valueOf(noUseful.get("message"))).contains("nothing useful");

        Map<String, Object> refine = client.refineFromChat(List.of(Map.of("role", "user", "content", "x")), null);
        assertThat(refine.get("status")).isEqualTo("AI_SERVICE_UNAVAILABLE");

        assertThat(client.recommend(List.of(), "2026-09-01", null).status()).isEqualTo("UNAVAILABLE");
        assertThat(client.recommend(List.of(Map.of("name", "MBS")), "2026-09-01", null).status())
                .isEqualTo("AI_SERVICE_UNAVAILABLE");
        assertThat(client.planItinerary(List.of(Map.of("name", "MBS")), "2026-09-01", 1).status())
                .isEqualTo("AI_SERVICE_UNAVAILABLE");
    }

    @Test
    void aiPlanningClient_parsesSuccessfulRecommendAndPlan() {
        server.createContext("/recommend", exchange -> writeJson(exchange, 200, """
                {
                  "status": "OK",
                  "weather_summary": "sunny",
                  "ordered_stops": [],
                  "suggested_additions": [
                    {
                      "name": "Garden",
                      "type": "attraction",
                      "lat": 1.28,
                      "lng": 103.86,
                      "distance_km": 1.1,
                      "reason": "nearby",
                      "activities": ["walk"]
                    }
                  ]
                }
                """));
        server.createContext("/plan-itinerary", exchange -> writeJson(exchange, 200, """
                {
                  "status": "OK",
                  "days": [
                    {
                      "day": 1,
                      "date": "2026-09-01",
                      "weather_summary": "ok",
                      "stops": [
                        { "name": "MBS", "order": 1, "time_of_day": "morning", "reason": "start" }
                      ]
                    }
                  ]
                }
                """));

        AiPlanningClientHttp client = new AiPlanningClientHttp(baseUrl);
        AiRecommendResult recommend = client.recommend(
                List.of(Map.of("name", "MBS")), "2026-09-01", null, "hybrid", 3, null, "Singapore");
        assertThat(recommend.status()).isEqualTo("OK");
        assertThat(recommend.suggestedAdditions()).hasSize(1);

        AiPlanItineraryResult plan = client.planItinerary(List.of(Map.of("name", "MBS")), "2026-09-01", 1);
        assertThat(plan.days()).hasSize(1);
        assertThat(plan.days().get(0).stops().get(0).name()).isEqualTo("MBS");
    }

    @Test
    void routingClient_usesOsrmPayload_andFallsBackOnFailure() {
        server.createContext("/route/v1/driving/", exchange -> writeJson(exchange, 200, """
                {
                  "code": "Ok",
                  "routes": [
                    { "distance": 3500.0, "duration": 720.0 }
                  ]
                }
                """));

        RoutingClientHttp client = new RoutingClientHttp(baseUrl);
        Optional<RoutingClient.RouteEstimate> estimate = client.estimate(
                new BigDecimal("1.28"), new BigDecimal("103.85"),
                new BigDecimal("1.29"), new BigDecimal("103.86")
        );

        assertThat(estimate).isPresent();
        assertThat(estimate.get().distanceKm()).isEqualByComparingTo("3.50");
        assertThat(estimate.get().durationMinutes()).isEqualTo(12);
        assertThat(estimate.get().googleMapLink()).contains("google.com/maps/dir");

        assertThat(client.estimate(null, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)).isEmpty();
    }

    @Test
    void routingClient_fallsBackToHaversineWhenOsrmRejects() {
        server.createContext("/route/v1/driving/", exchange -> writeJson(exchange, 200, """
                { "code": "NoRoute", "routes": [] }
                """));

        RoutingClientHttp client = new RoutingClientHttp(baseUrl);
        Optional<RoutingClient.RouteEstimate> estimate = client.estimate(
                new BigDecimal("1.28"), new BigDecimal("103.85"),
                new BigDecimal("1.29"), new BigDecimal("103.86")
        );

        assertThat(estimate).isPresent();
        assertThat(estimate.get().distanceKm()).isPositive();
        assertThat(estimate.get().durationMinutes()).isGreaterThanOrEqualTo(1);
    }

    private static void writeJson(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void writeText(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
