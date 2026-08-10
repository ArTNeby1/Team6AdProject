package com.loomytrip.backend.dto.response;

import com.loomytrip.backend.entity.User;

public record AuthResponse(
        String accessToken,
        String tokenType,
        Long userId,
        String username,
        String email,
        String travelStyle,
        String preferTransport
) {
    public static AuthResponse bearer(String token, User user) {
        return new AuthResponse(
                token, "Bearer", user.getId(), user.getUsername(), user.getEmail(),
                user.getTravelStyle(), user.getPreferTransport()
        );
    }
}
