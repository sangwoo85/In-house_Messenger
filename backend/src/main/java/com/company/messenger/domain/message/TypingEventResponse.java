package com.company.messenger.domain.message;

public record TypingEventResponse(
        Long channelId,
        String userId,
        boolean typing
) {
}

