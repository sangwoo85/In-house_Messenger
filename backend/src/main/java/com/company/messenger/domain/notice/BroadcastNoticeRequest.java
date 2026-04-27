package com.company.messenger.domain.notice;

import jakarta.validation.constraints.NotBlank;

public record BroadcastNoticeRequest(
        @NotBlank String title,
        @NotBlank String content,
        @NotBlank String sender
) {
}

