package com.loomytrip.backend.client;

import java.util.List;
import java.util.Map;

/**
 * Contract for the Python/LLM planning agent (F-03 / F-09).
 */
public interface AiPlanningClient {

    Map<String, Object> extractTravelInfo(String rawContent, String sourceUrl);

    Map<String, Object> generateDailyItinerary(Long tripId, List<Long> confirmedPlaceIds);
}
