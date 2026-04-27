package com.company.messenger.domain.channel;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateChannelRequest(
        String name,
        @NotNull ChannelType type,
        @NotEmpty List<String> memberUserIds
) {
}

