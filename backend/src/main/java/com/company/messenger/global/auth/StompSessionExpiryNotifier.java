package com.company.messenger.global.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnBean(SimpMessagingTemplate.class)
@RequiredArgsConstructor
public class StompSessionExpiryNotifier implements SessionExpiryNotifier {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void notifySessionExpired(String userId) {
        messagingTemplate.convertAndSendToUser(userId, "/queue/session-expired", Map.of(
                "type", "SESSION_EXPIRED",
                "message", "다른 기기에서 로그인되어 현재 세션이 만료되었습니다."
        ));
    }
}
