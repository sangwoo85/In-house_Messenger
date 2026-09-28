package com.company.messenger.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix = "app.redis")
public record RedisKeyspace(String keyPrefix) {
    public RedisKeyspace { keyPrefix = keyPrefix == null || keyPrefix.isBlank() ? "company-messenger:" : keyPrefix; }
    public String key(String suffix) { return keyPrefix + suffix; }
}
