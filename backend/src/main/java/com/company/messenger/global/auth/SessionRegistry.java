package com.company.messenger.global.auth;

import java.util.Optional;

public interface SessionRegistry {
    Optional<String> findSessionId(String userId);

    void save(String userId, String sessionId);

    void delete(String userId);
}

