package com.company.messenger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties(prefix = "app.web")
public record WebProperties(List<String> allowedOrigins) {
    public WebProperties { allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins); }
}
