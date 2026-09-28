package com.company.messenger.domain.channel;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Defines a direct chat or named group; the authenticated creator is added to the supplied recipients. */
public record CreateChannelRequest(
        @jakarta.validation.constraints.Size(max = 100) String name,
        @NotNull ChannelType type,
        @NotEmpty @jakarta.validation.constraints.Size(max = 100) List<@jakarta.validation.constraints.NotBlank String> memberUserIds
) {
}

