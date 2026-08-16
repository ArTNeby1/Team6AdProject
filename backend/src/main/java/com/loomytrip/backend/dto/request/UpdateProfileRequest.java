package com.loomytrip.backend.dto.request;

public record UpdateProfileRequest(
        String username,
        Integer age,
        String gender
) {
}
