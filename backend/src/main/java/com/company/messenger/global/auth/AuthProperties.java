package com.company.messenger.global.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        long accessTokenExpirationMinutes,
        long refreshTokenExpirationDays,
        boolean cookieSecure
) {
    public Duration accessTokenExpiration() {
        return Duration.ofMinutes(accessTokenExpirationMinutes);
    }

    public Duration refreshTokenExpiration() {
        return Duration.ofDays(refreshTokenExpirationDays);
    }
}

