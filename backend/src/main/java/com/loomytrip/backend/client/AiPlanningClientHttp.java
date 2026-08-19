package com.loomytrip.backend.client;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls the local Python/LLM planning service (ML/app, FastAPI) over HTTP —
 * see ML/app/main.py `/extract-travel-info`. That service runs a 3-stage
 * local pipeline (chat noise filter -> local LLM extraction -> recommendation
 * agent, see ML/app/orchestrator.py) and only its result is meant to reach
 * this backend; the intermediate JSON never comes here.
 *
 * The Python service is a separate local process (uvicorn on port 8001 by
 * default) and may not be running in every environment (CI, a teammate's
 * machine without Ollama set up). Rather than let that take PlanningService
 * down, network/HTTP failures are caught and turned into a STUB-shaped
 * response so callers can still degrade gracefully.
 */
@Component
public class AiPlanningClientHttp implements AiPlanningClient {

    private final RestClient restClient;

    public AiPlanningClientHttp(@Value("${loomytrip.ai.base-url:http://localhost:8001}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(60).toMillis());
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> extractTravelInfo(String rawContent, String sourceUrl) {
        if (rawContent == null || rawContent.isBlank()) {
            return unavailableResponse("NO_CONTENT");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("raw_content", rawContent);
        body.put("source_url", sourceUrl);

        try {
            return restClient.post()
                    .uri("/extract-travel-info")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            return mapExtractionError(e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> refineFromChat(List<Map<String, String>> messages, String preferenceText) {
        Map<String, Object> body = new HashMap<>();
        body.put("messages", messages);
        body.put("preference_text", preferenceText);

        try {
            return restClient.post()
                    .uri("/refine")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            return mapExtractionError(e);
        }
    }

    /**
     * Both `/extract-travel-info` and `/refine` share the same failure shape (see
     * ML/app/main.py `_extraction_response`): a 422 with a {@code NO_USEFUL_CONTENT:}-
     * prefixed body means the user's text had nothing travel-related in it — a 4xx input
     * problem, not the AI service being down. Everything else (real 5xx, timeout, ML not
     * running at all) degrades to the generic unavailable stub, same as before. Callers
     * (PlanningService) branch on {@code status} to turn NO_USEFUL_CONTENT into a proper
     * "please describe your trip" error instead of silently creating an empty session.
     */
    private Map<String, Object> mapExtractionError(RestClientException e) {
        if (e instanceof HttpClientErrorException httpError
                && httpError.getStatusCode().value() == 422) {
            String body = httpError.getResponseBodyAsString();
            int prefixIndex = body.indexOf("NO_USEFUL_CONTENT:");
            if (prefixIndex >= 0) {
                String message = body.substring(prefixIndex + "NO_USEFUL_CONTENT:".length()).trim();
                // FastAPI wraps our detail string in {"detail": "..."} JSON — strip the
                // trailing `"}` so callers get the plain message, not raw JSON escaping.
                message = message.replaceAll("\"\\s*}\\s*$", "").trim();
                return Map.of("status", "NO_USEFUL_CONTENT", "message", message, "places", List.of());
            }
        }
        return unavailableResponse("AI_SERVICE_UNAVAILABLE");
    }

    @Override
    public AiRecommendResult recommend(
            List<Map<String, Object>> places,
            String date,
            String preferenceText,
            String mode,
            Integer topN,
            Double maxDistanceKm,
            String destination
    ) {
        if (places == null || places.isEmpty()) {
            return new AiRecommendResult("UNAVAILABLE", null, List.of(), List.of());
        }
        Map<String, Object> body = new HashMap<>();
        body.put("places", places);
        body.put("date", date);
        body.put("preference_text", preferenceText);
        body.put("mode", mode != null ? mode : "hybrid");
        body.put("top_n", topN != null ? topN : 3);
        body.put("max_distance_km", maxDistanceKm);
        body.put("destination", destination != null ? destination : "Singapore");

        try {
            return restClient.post()
                    .uri("/recommend")
                    .body(body)
                    .retrieve()
                    .body(AiRecommendResult.class);
        } catch (RestClientException e) {
            return new AiRecommendResult("AI_SERVICE_UNAVAILABLE", null, List.of(), List.of());
        }
    }

    @Override
    public AiPlanItineraryResult planItinerary(List<Map<String, Object>> places, String startDate, int numDays) {
        if (places == null || places.isEmpty()) {
            return new AiPlanItineraryResult("UNAVAILABLE", List.of());
        }
        Map<String, Object> body = new HashMap<>();
        body.put("places", places);
        body.put("start_date", startDate);
        body.put("num_days", numDays);

        try {
            return restClient.post()
                    .uri("/plan-itinerary")
                    .body(body)
                    .retrieve()
                    .body(AiPlanItineraryResult.class);
        } catch (RestClientException e) {
            return new AiPlanItineraryResult("AI_SERVICE_UNAVAILABLE", List.of());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> evaluateExtraction(String sourceText, List<String> predictedPlaces) {
        if (sourceText == null || sourceText.isBlank()) {
            return Map.of("status", "NO_CONTENT");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("source_text", sourceText);
        body.put("predicted_places", predictedPlaces == null ? List.of() : predictedPlaces);

        try {
            return restClient.post()
                    .uri("/evaluate-extraction")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            return Map.of("status", "AI_SERVICE_UNAVAILABLE");
        }
    }

    private Map<String, Object> unavailableResponse(String status) {
        return Map.of(
                "status", status,
                "places", List.of(),
                "activities", List.of()
        );
    }
}
