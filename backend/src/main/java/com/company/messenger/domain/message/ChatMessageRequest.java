package com.company.messenger.domain.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChatMessageRequest(
        @NotNull Long channelId,
        @NotBlank String content,
        @NotNull MessageType type,
        Long fileId
) {
}
