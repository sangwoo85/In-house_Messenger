package com.company.messenger.global.auth;

public record JwtTokenClaims(
        String userId,
        String sessionId,
        TokenType tokenType
) {
}

