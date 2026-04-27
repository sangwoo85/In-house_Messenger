package com.company.messenger.global.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(SessionExpiryNotifier.class)
public class NoOpSessionExpiryNotifier implements SessionExpiryNotifier {

    @Override
    public void notifySessionExpired(String userId) {
    }
}
