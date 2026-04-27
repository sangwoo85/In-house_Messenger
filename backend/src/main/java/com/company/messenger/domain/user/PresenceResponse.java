package com.company.messenger.domain.user;

public record PresenceResponse(
        String userId,
        UserStatus status
) {
}

