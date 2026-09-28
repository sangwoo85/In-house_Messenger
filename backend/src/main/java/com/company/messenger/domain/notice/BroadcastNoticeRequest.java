package com.company.messenger.domain.notice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A persisted company-wide notice with a business type and desktop delivery option. */
public record BroadcastNoticeRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 10000) String content,
        @NotBlank @Size(max = 100) String sender,
        @NotBlank @Size(max = 50) String notificationType,
        NotificationDisplayMode displayMode
) {
    /** Uses the native desktop notification unless the sending system chooses otherwise. */
    public BroadcastNoticeRequest {
        notificationType = notificationType == null ? "GENERAL" : notificationType.trim();
        displayMode = displayMode == null ? NotificationDisplayMode.SYSTEM : displayMode;
    }

    /** Keeps existing internal callers on the default delivery mode. */
    public BroadcastNoticeRequest(String title, String content, String sender) {
        this(title, content, sender, null, null);
    }
}
