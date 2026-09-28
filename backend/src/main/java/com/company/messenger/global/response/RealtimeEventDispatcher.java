package com.company.messenger.global.response;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.company.messenger.domain.message.MessageResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class RealtimeEventDispatcher {
    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void deliver(RealtimeEvents.Delivery event) {
        var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        Object payload = event.payload();
        if (payload instanceof java.util.Map<?, ?> envelope) payload = envelope.get("message");
        if (payload instanceof MessageResponse message) headers.setHeader("messengerChannelId", message.channelId());
        headers.setLeaveMutable(true);
        try {
            if (event.userId() == null) {
                messagingTemplate.convertAndSend(event.destination(), event.payload(), headers.getMessageHeaders());
            } else {
                messagingTemplate.convertAndSendToUser(event.userId(), event.destination(), event.payload(), headers.getMessageHeaders());
            }
        } catch (RuntimeException failure) {
            // The data is committed. Clients reconcile from REST after reconnect or periodic refresh.
            log.warn("Realtime delivery failed for destination {}", event.destination(), failure);
        }
    }
}
