package com.loomytrip.backend.service;

import com.loomytrip.backend.dto.response.MapConfigResponse;
import com.loomytrip.backend.dto.response.RecommendationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MapService {

    private final RecommendationService recommendationService;
    private final String tileUrlTemplate;
    private final String attribution;
    private final double defaultLatitude;
    private final double defaultLongitude;
    private final int defaultZoom;

    public MapService(
            RecommendationService recommendationService,
            @Value("${loomytrip.map.tile-url-template:https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png}") String tileUrlTemplate,
            @Value("${loomytrip.map.attribution:© OpenStreetMap contributors}") String attribution,
            @Value("${loomytrip.map.default-latitude:1.3521}") double defaultLatitude,
            @Value("${loomytrip.map.default-longitude:103.8198}") double defaultLongitude,
            @Value("${loomytrip.map.default-zoom:12}") int defaultZoom
    ) {
        this.recommendationService = recommendationService;
        this.tileUrlTemplate = tileUrlTemplate;
        this.attribution = attribution;
        this.defaultLatitude = defaultLatitude;
        this.defaultLongitude = defaultLongitude;
        this.defaultZoom = defaultZoom;
    }

    public MapConfigResponse getConfig() {
        return new MapConfigResponse(
                tileUrlTemplate,
                attribution,
                defaultLatitude,
                defaultLongitude,
                defaultZoom
        );
    }

    public RecommendationResponse nearby(double lat, double lng, String mode, Integer topN) {
        return recommendationService.recommendNearCoordinates(lat, lng, mode, topN);
    }
}
