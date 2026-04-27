package com.company.messenger.domain.notice;

import java.util.List;

public record UserNotificationPageResponse(
        List<UserNotificationResponse> items,
        int page,
        int size,
        long totalElements
) {
}

