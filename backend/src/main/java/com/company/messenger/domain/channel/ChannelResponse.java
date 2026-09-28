package com.company.messenger.domain.channel;

import java.util.List;
import java.time.LocalDateTime;

/** Summarizes participants and unread counts; lastMessageAt is null until the first message is sent. */
public record ChannelResponse(
        Long id,
        String name,
        ChannelType type,
        List<String> members,
        long unreadCount,
        String ownerUserId,
        LocalDateTime lastMessageAt
) {
}
