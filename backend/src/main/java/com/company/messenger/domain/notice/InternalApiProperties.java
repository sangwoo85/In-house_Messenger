package com.company.messenger.domain.notice;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record InternalApiProperties(
        String jwtSecret,
        String internalApiKey
) {
}

