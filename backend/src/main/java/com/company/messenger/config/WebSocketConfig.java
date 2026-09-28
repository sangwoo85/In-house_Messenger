package com.company.messenger.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final RealtimeSessionGuard sessionGuard;
    private final WebProperties webProperties;

    public WebSocketConfig(StompAuthChannelInterceptor stompAuthChannelInterceptor, RealtimeSessionGuard sessionGuard, WebProperties webProperties) {
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
        this.sessionGuard = sessionGuard;
        this.webProperties = webProperties;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        var origins = new java.util.ArrayList<>(webProperties.allowedOrigins());
        origins.add("null");
        registry.addEndpoint("/ws").setAllowedOriginPatterns(origins.toArray(String[]::new));
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(sessionGuard);
    }

    @Override
    public void configureWebSocketTransport(org.springframework.web.socket.config.annotation.WebSocketTransportRegistration registry) {
        registry.addDecoratorFactory(handler -> new org.springframework.web.socket.handler.WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(org.springframework.web.socket.WebSocketSession session) throws Exception {
                sessionGuard.opened(session);
                super.afterConnectionEstablished(session);
            }
            @Override
            public void afterConnectionClosed(org.springframework.web.socket.WebSocketSession session, org.springframework.web.socket.CloseStatus status) throws Exception {
                sessionGuard.closed(session.getId());
                super.afterConnectionClosed(session, status);
            }
        });
    }
}
