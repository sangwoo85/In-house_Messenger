package com.company.messenger.domain.message;

import jakarta.validation.constraints.NotNull;

public record TypingIndicatorRequest(
        @NotNull Long channelId,
        boolean typing
) {
}

