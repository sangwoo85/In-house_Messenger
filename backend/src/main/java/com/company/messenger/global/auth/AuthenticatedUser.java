package com.company.messenger.global.auth;

import java.security.Principal;

public record AuthenticatedUser(
        String userId,
        String sessionId
) implements Principal {
    @Override
    public String getName() {
        return userId;
    }
}
