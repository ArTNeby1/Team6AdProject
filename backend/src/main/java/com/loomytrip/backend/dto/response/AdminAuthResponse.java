package com.loomytrip.backend.dto.response;

import com.loomytrip.backend.entity.AdminRole;

public record AdminAuthResponse(
        String accessToken,
        String tokenType,
        Long adminId,
        String email,
        AdminRole role
) {
    public static AdminAuthResponse bearer(String token, Long adminId, String email, AdminRole role) {
        return new AdminAuthResponse(token, "Bearer", adminId, email, role);
    }
}
