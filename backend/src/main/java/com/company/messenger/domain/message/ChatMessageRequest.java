package com.company.messenger.domain.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChatMessageRequest(
        @NotNull Long channelId,
        @NotBlank @Size(max = 4000) String content,
        @NotNull MessageType type,
        Long fileId,
        @jakarta.validation.constraints.Pattern(regexp = "[a-fA-F0-9-]{36}") String clientRequestId,
        @jakarta.validation.constraints.Positive Long replyToMessageId
) {
    public ChatMessageRequest(Long channelId, String content, MessageType type, Long fileId) {
        this(channelId, content, type, fileId, null, null);
    }

    public ChatMessageRequest(Long channelId, String content, MessageType type, Long fileId, String clientRequestId) {
        this(channelId, content, type, fileId, clientRequestId, null);
    }
}
