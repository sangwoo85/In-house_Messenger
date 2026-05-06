package com.company.messenger.global.external;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.external")
public record ExternalAuthProperties(
        String authBaseUrl,
        int authTimeoutSeconds,
        String authLoginPath,
        String userListPath
) {
}
