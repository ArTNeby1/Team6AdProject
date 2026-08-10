package com.loomytrip.backend.dto.request;

public record UpdatePreferencesRequest(
        String travelStyle,
        String preferTransport
) {
}
