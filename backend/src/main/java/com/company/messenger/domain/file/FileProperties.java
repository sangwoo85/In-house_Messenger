package com.company.messenger.domain.file;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/** Binds external attachment storage and image/general file size limits. */
@ConfigurationProperties(prefix = "app.file")
public record FileProperties(
        String storagePath,
        long imageMaxBytes,
        long otherMaxBytes
) {
    /** Resolves the configured server-side directory used for generated attachment filenames. */
    public Path storageDirectory() {
        return Path.of(storagePath);
    }
}

