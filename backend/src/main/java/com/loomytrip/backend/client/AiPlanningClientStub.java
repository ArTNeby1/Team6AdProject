package com.loomytrip.backend.client;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AiPlanningClientStub implements AiPlanningClient {

    @Override
    public Map<String, Object> extractTravelInfo(String rawContent, String sourceUrl) {
        return Map.of(
                "status", "STUB",
                "places", List.of(),
                "activities", List.of()
        );
    }

    @Override
    public Map<String, Object> generateDailyItinerary(Long tripId, List<Long> confirmedPlaceIds) {
        return Map.of(
                "status", "STUB",
                "tripId", tripId,
                "days", List.of()
        );
    }
}
