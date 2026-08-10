package com.loomytrip.backend.dto.response;

import com.loomytrip.backend.entity.UserRole;
import java.time.Instant;

/**
 * Read-only projection of a traveler account for the admin user list.
 * Deliberately omits {@code passwordHash} and any other sensitive field.
 */
public record AdminUserResponse(
        Long id,
        String username,
        String email,
        Integer age,
        String gender,
        UserRole role,
        Instant createdAt
) {
}
