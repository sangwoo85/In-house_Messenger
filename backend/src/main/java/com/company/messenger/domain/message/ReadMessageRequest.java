package com.company.messenger.domain.message;

import jakarta.validation.constraints.NotNull;

public record ReadMessageRequest(
        @NotNull Long messageId
) {
}

