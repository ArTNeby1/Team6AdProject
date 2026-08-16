package com.loomytrip.backend.service;

import com.loomytrip.backend.client.AiPlanningClient;
import com.loomytrip.backend.client.AiRecommendResult;
import com.loomytrip.backend.dto.response.RecommendationItemResponse;
import com.loomytrip.backend.dto.response.RecommendationResponse;
import com.loomytrip.backend.entity.Destination;
import com.loomytrip.backend.entity.User;
import com.loomytrip.backend.exception.ApiException;
import com.loomytrip.backend.repository.DestinationRepository;
import com.loomytrip.backend.repository.TripScheduleRepository;
import com.loomytrip.backend.repository.UserRepository;
import com.loomytrip.backend.util.SecurityUtils;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendationService {

    private static final String DEFAULT_MODE = "hybrid";
    private static final int DEFAULT_TOP_N = 5;

    private final DestinationRepository destinationRepository;
    private final UserRepository userRepository;
    private final TripScheduleRepository tripScheduleRepository;
    private final AiPlanningClient aiPlanningClient;
    private final DestinationService destinationService;

    public RecommendationService(
            DestinationRepository destinationRepository,
            UserRepository userRepository,
            TripScheduleRepository tripScheduleRepository,
            AiPlanningClient aiPlanningClient,
            DestinationService destinationService
    ) {
        this.destinationRepository = destinationRepository;
        this.userRepository = userRepository;
        this.tripScheduleRepository = tripScheduleRepository;
        this.aiPlanningClient = aiPlanningClient;
        this.destinationService = destinationService;
    }

    /**
     * F-18 standalone recommendations: uses {@code destinationId} as the anchor place,
     * proxies to ML {@code POST /recommend}, and returns {@code suggested_additions}.
     * Falls back to a simple DB listing when ML is unavailable or {@code destinationId}
     * is omitted.
     */
    @Transactional
    public RecommendationResponse recommendNearby(Long destinationId, String mode, Integer topN) {
        if (destinationId == null) {
            return fallbackFromDatabase(null);
        }

        Destination anchor = destinationRepository.findById(destinationId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "DESTINATION_NOT_FOUND",
                        "Destination not found: " + destinationId
                ));

        try {
            anchor = destinationService.ensureGeocoded(anchor);
        } catch (ApiException ex) {
            return fallbackFromDatabase(destinationId);
        }

        return recommendFromAnchor(
                anchor.getName(),
                anchor.getCategory(),
                anchor.getLatitude(),
                anchor.getLongitude(),
                mode,
                topN,
                destinationId
        );
    }

    @Transactional(readOnly = true)
    public RecommendationResponse recommendNearCoordinates(double lat, double lng, String mode, Integer topN) {
        return recommendFromAnchor(
                "Map center",
                "other",
                BigDecimal.valueOf(lat),
                BigDecimal.valueOf(lng),
                mode,
                topN,
                null
        );
    }

    private RecommendationResponse recommendFromAnchor(
            String name,
            String category,
            BigDecimal lat,
            BigDecimal lng,
            String mode,
            Integer topN,
            Long excludeDestinationId
    ) {
        Map<String, Object> place = new LinkedHashMap<>();
        place.put("name", name);
        place.put("type", category != null ? category : "attraction");
        place.put("lat", lat);
        place.put("lng", lng);
        place.put("activities", List.of());

        String resolvedMode = mode == null || mode.isBlank() ? DEFAULT_MODE : mode.trim();
        int resolvedTopN = topN == null || topN < 1 ? DEFAULT_TOP_N : Math.min(topN, 10);

        AiRecommendResult result = aiPlanningClient.recommend(
                List.of(place),
                null,
                buildPreferenceText(),
                resolvedMode,
                resolvedTopN,
                null,
                "Singapore"
        );

        if (result == null || !"OK".equalsIgnoreCase(result.status()) || result.suggestedAdditions().isEmpty()) {
            return fallbackFromDatabase(excludeDestinationId);
        }

        // Same reasoning as PlanningService.confirmSession(): the AI only knows about the
        // single anchor place passed in above, not everywhere else the user has already
        // planned to go, so re-filter against the traveler's full trip history here too.
        Set<String> visitedNames = currentVisitedNames();
        List<RecommendationItemResponse> items = result.suggestedAdditions().stream()
                .filter(s -> !visitedNames.contains(s.name().trim().toLowerCase(Locale.ROOT)))
                .map(this::toItem)
                .toList();
        return new RecommendationResponse(items);
    }

    private Set<String> currentVisitedNames() {
        User user = userRepository.findByEmail(SecurityUtils.currentUserEmail()).orElse(null);
        if (user == null) {
            return Collections.emptySet();
        }
        return tripScheduleRepository.findVisitedDestinationNamesByUserId(user.getId());
    }

    private RecommendationItemResponse toItem(AiRecommendResult.SuggestedAddition suggestion) {
        Long existingId = destinationRepository.findByNameContainingIgnoreCase(suggestion.name()).stream()
                .filter(candidate -> candidate.getName().equalsIgnoreCase(suggestion.name()))
                .map(Destination::getId)
                .findFirst()
                .orElse(null);

        return new RecommendationItemResponse(
                existingId,
                suggestion.name(),
                suggestion.name(),
                suggestion.lat(),
                suggestion.lng(),
                suggestion.type(),
                suggestion.distanceKm(),
                suggestion.reason(),
                suggestion.activities() == null ? List.of() : suggestion.activities()
        );
    }

    private String buildPreferenceText() {
        User user = userRepository.findByEmail(SecurityUtils.currentUserEmail()).orElse(null);
        if (user == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (user.getTravelStyle() != null && !user.getTravelStyle().isBlank()) {
            parts.add("travel_style=" + user.getTravelStyle());
        }
        if (user.getPreferTransport() != null && !user.getPreferTransport().isBlank()) {
            parts.add("prefer_transport=" + user.getPreferTransport());
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private RecommendationResponse fallbackFromDatabase(Long excludeDestinationId) {
        List<RecommendationItemResponse> items = destinationRepository.findAll().stream()
                .filter(destination -> excludeDestinationId == null || !destination.getId().equals(excludeDestinationId))
                .limit(DEFAULT_TOP_N)
                .map(destination -> new RecommendationItemResponse(
                        destination.getId(),
                        destination.getName(),
                        destination.getAddress(),
                        destination.getLatitude(),
                        destination.getLongitude(),
                        destination.getCategory(),
                        null,
                        null,
                        List.of()
                ))
                .toList();
        return new RecommendationResponse(items);
    }
}
