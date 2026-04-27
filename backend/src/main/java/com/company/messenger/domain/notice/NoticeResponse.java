package com.company.messenger.domain.notice;

import java.time.LocalDateTime;

public record NoticeResponse(
        Long id,
        String title,
        String content,
        String sender,
        LocalDateTime createdAt
) {
    public static NoticeResponse from(Notice notice) {
        return new NoticeResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getContent(),
                notice.getSender(),
                notice.getCreatedAt()
        );
    }
}

