package com.loomytrip.backend.dto.response;

public record AuthResponse(
        String accessToken,
        String tokenType,
        Long userId,
        String email
) {
    public static AuthResponse bearer(String token, Long userId, String email) {
        return new AuthResponse(token, "Bearer", userId, email);
    }
}
