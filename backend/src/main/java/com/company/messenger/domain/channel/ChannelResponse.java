package com.company.messenger.domain.channel;

import java.util.List;

public record ChannelResponse(
        Long id,
        String name,
        ChannelType type,
        List<String> members,
        long unreadCount
) {
}
