package com.loomytrip.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AdminLoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {
}
