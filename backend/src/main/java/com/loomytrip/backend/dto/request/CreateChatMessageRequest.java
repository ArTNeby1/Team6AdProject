package com.loomytrip.backend.dto.request;

import com.loomytrip.backend.entity.ChatRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateChatMessageRequest(
        @NotNull ChatRole role,
        @NotBlank String content
) {
}
