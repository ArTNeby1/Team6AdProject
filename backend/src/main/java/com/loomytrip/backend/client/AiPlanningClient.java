package com.loomytrip.backend.client;

import java.util.List;
import java.util.Map;

/**
 * Contract for the Python/LLM planning agent (F-03 / F-09).
 */
public interface AiPlanningClient {

    Map<String, Object> extractTravelInfo(String rawContent, String sourceUrl);

    /**
     * Multi-turn refinement: same response shape as {@link #extractTravelInfo}, but the
     * Python service runs the chat history through {@code chat_filter} first (see
     * ML/app/main.py `/refine`).
     */
    Map<String, Object> refineFromChat(List<Map<String, String>> messages, String preferenceText);

    /**
     * Calls ML/app/main.py `POST /recommend` — order the user's confirmed places (by
     * weather + distance) and suggest nearby additions (F-18). Places must already carry
     * real lat/lng where available.
     */
    default AiRecommendResult recommend(List<Map<String, Object>> places, String date, String preferenceText) {
        return recommend(places, date, preferenceText, "hybrid", 3, null, "Singapore");
    }

    /**
     * Full recommend call with ML tuning knobs ({@code mode}, {@code topN}, etc.).
     * Used by standalone {@code GET /recommendations} as well as planning confirm.
     */
    AiRecommendResult recommend(
            List<Map<String, Object>> places,
            String date,
            String preferenceText,
            String mode,
            Integer topN,
            Double maxDistanceKm,
            String destination
    );

    /**
     * Calls ML/app/main.py {@code POST /plan-itinerary} to redistribute confirmed stops
     * across multiple days (F-09).
     */
    AiPlanItineraryResult planItinerary(List<Map<String, Object>> places, String startDate, int numDays);

    /**
     * Calls ML/app/main.py {@code POST /evaluate-extraction} — content-level accuracy scoring
     * for one import (admin LLM Evaluation console). The ML service uses an LLM-as-judge to
     * derive the gold place list from {@code sourceText}, then scores {@code predictedPlaces}
     * against it, returning precision / recall / f1 / groundedness plus the matched / missed /
     * spurious breakdown. A {@code default} so alternative clients (tests, stubs) need not
     * implement it — they degrade to an unavailable stub.
     */
    default Map<String, Object> evaluateExtraction(String sourceText, List<String> predictedPlaces) {
        return Map.of("status", "AI_SERVICE_UNAVAILABLE");
    }
}
