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
    AiRecommendResult recommend(List<Map<String, Object>> places, String date, String preferenceText);

    Map<String, Object> generateDailyItinerary(Long tripId, List<Long> confirmedPlaceIds);
}
