package com.company.messenger.global.auth;

import java.time.Duration;
import java.util.Optional;

public interface RefreshTokenStore {
    void save(String userId, String refreshToken, Duration ttl);

    Optional<String> find(String userId);

    void delete(String userId);
}

