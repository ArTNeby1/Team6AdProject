package com.loomytrip.backend.dto.response;

public record UserProfileResponse(
        Long id,
        String username,
        String email,
        Integer age,
        String gender,
        String travelStyle,
        String preferTransport
) {
}
