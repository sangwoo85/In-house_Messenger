package com.company.messenger.domain.notice;

import java.time.LocalDateTime;

public record NoticeResponse(
        Long id,
        String title,
        String content,
        String sender,
        String notificationType,
        NotificationDisplayMode displayMode,
        LocalDateTime createdAt
) {
    /** Includes the persisted delivery options for both HTTP history and realtime delivery. */
    public static NoticeResponse from(Notice notice) {
        return new NoticeResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getContent(),
                notice.getSender(),
                notice.getNotificationType(),
                notice.getDisplayMode(),
                notice.getCreatedAt()
        );
    }
}

