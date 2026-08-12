package com.loomytrip.backend.dto.response;

public record MapConfigResponse(
        String tileUrlTemplate,
        String attribution,
        double defaultLatitude,
        double defaultLongitude,
        int defaultZoom
) {
}
