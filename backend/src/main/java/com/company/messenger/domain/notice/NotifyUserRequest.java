package com.company.messenger.domain.notice;

import jakarta.validation.constraints.NotBlank;

public record NotifyUserRequest(
        @NotBlank String targetUserId,
        @NotBlank String title,
        @NotBlank String content,
        String linkUrl
) {
}

