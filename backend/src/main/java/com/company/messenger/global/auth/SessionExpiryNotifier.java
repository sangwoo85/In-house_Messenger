package com.company.messenger.global.auth;

public interface SessionExpiryNotifier {
    void notifySessionExpired(String userId);
}

