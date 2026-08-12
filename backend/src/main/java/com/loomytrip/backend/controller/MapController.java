package com.loomytrip.backend.controller;

import com.loomytrip.backend.dto.response.CrowdHintResponse;
import com.loomytrip.backend.dto.response.MapConfigResponse;
import com.loomytrip.backend.dto.response.RecommendationResponse;
import com.loomytrip.backend.service.CrowdHintService;
import com.loomytrip.backend.service.MapService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/map")
public class MapController {

    private final MapService mapService;
    private final CrowdHintService crowdHintService;

    public MapController(MapService mapService, CrowdHintService crowdHintService) {
        this.mapService = mapService;
        this.crowdHintService = crowdHintService;
    }

    /**
     * Frontend Leaflet config — tiles are loaded directly from OSM; backend only documents the URL.
     */
    @GetMapping("/config")
    public MapConfigResponse config() {
        return mapService.getConfig();
    }

    /**
     * Nearby POI suggestions around a map coordinate (proxies ML {@code POST /recommend}).
     */
    @GetMapping("/nearby")
    public RecommendationResponse nearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(required = false, defaultValue = "nearby") String mode,
            @RequestParam(required = false, defaultValue = "5") Integer topN
    ) {
        return mapService.nearby(lat, lng, mode, topN);
    }

    /**
     * Seasonal crowd/traffic hint for heatmap UI (national quarterly proxy, F-33).
     */
    @GetMapping("/crowd")
    public CrowdHintResponse crowd(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        LocalDate target = date != null ? date : LocalDate.now();
        return crowdHintService.getHint(target);
    }
}
