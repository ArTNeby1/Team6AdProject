package com.loomytrip.backend.client;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
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
            return unavailableResponse("AI_SERVICE_UNAVAILABLE");
        }
    }

    @Override
    public Map<String, Object> generateDailyItinerary(Long tripId, List<Long> confirmedPlaceIds) {
        // 行程生成（F-09）还没在 Python 侧实现——目前 orchestrator.py 只有
        // 抽取 + 推荐两个 agent，没有按天排程的第三个 agent，先保留 stub 行为。
        return Map.of(
                "status", "STUB",
                "tripId", tripId,
                "days", List.of()
        );
    }

    private Map<String, Object> unavailableResponse(String status) {
        return Map.of(
                "status", status,
                "places", List.of(),
                "activities", List.of()
        );
    }
}
