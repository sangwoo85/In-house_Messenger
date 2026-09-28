package com.company.messenger.global.response;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RealtimeEvents {
    private final ApplicationEventPublisher publisher;

    public void topic(String destination, Object payload) {
        publisher.publishEvent(new Delivery(null, destination, payload));
    }

    public void user(String userId, String destination, Object payload) {
        publisher.publishEvent(new Delivery(userId, destination, payload));
    }

    public record Delivery(String userId, String destination, Object payload) { }
}
