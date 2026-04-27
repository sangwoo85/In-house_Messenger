package com.company.messenger.domain.file;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "app.file")
public record FileProperties(
        String storagePath,
        long imageMaxBytes,
        long otherMaxBytes
) {
    public Path storageDirectory() {
        return Path.of(storagePath);
    }
}

