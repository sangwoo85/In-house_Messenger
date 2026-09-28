package com.company.messenger.domain.notice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A business-system notification; omitted delivery options retain the original system toast. */
public record NotifyUserRequest(
        @NotBlank @Size(max = 50) String targetUserId,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 10000) String content,
        @Size(max = 500) String linkUrl,
        @NotBlank @Size(max = 50) String notificationType,
        NotificationDisplayMode displayMode
) {
    /** Supplies backward-compatible defaults before Bean Validation runs. */
    public NotifyUserRequest {
        notificationType = notificationType == null ? "GENERAL" : notificationType.trim();
        displayMode = displayMode == null ? NotificationDisplayMode.SYSTEM : displayMode;
    }

    /** Keeps existing internal callers on the default delivery mode. */
    public NotifyUserRequest(String targetUserId, String title, String content, String linkUrl) {
        this(targetUserId, title, content, linkUrl, null, null);
    }
}
