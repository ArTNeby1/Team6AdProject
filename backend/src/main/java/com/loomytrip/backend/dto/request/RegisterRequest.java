package com.loomytrip.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        String username,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        Integer age,
        String gender
) {
}
