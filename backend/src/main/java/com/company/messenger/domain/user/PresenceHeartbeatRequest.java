package com.company.messenger.domain.user;

import jakarta.validation.constraints.NotNull;

public record PresenceHeartbeatRequest(
        @NotNull UserStatus status
) {
}

