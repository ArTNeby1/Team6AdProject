package com.loomytrip.backend.controller;

import com.loomytrip.backend.dto.response.RecommendationResponse;
import com.loomytrip.backend.service.RecommendationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping
    public RecommendationResponse recommend(
            @RequestParam(required = false) Long destinationId,
            @RequestParam(required = false, defaultValue = "hybrid") String mode,
            @RequestParam(required = false, defaultValue = "5") Integer topN
    ) {
        return recommendationService.recommendNearby(destinationId, mode, topN);
    }
}
