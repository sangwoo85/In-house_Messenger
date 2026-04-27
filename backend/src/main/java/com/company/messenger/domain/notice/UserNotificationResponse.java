package com.company.messenger.domain.notice;

import java.time.LocalDateTime;

public record UserNotificationResponse(
        Long id,
        String title,
        String content,
        String linkUrl,
        boolean read,
        LocalDateTime createdAt
) {
    public static UserNotificationResponse from(UserNotification notification) {
        return new UserNotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getContent(),
                notification.getLinkUrl(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}

