package com.company.messenger.config;

import com.company.messenger.domain.channel.ChannelMemberRepository;
import com.company.messenger.global.auth.AuthenticatedUser;
import com.company.messenger.global.auth.SessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class RealtimeSessionGuard implements ChannelInterceptor {
    private final SessionRegistry sessionRegistry;
    private final ChannelMemberRepository memberships;
    private final ConcurrentHashMap<String, WebSocketSession> sockets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AuthenticatedUser> users = new ConcurrentHashMap<>();

    public void opened(WebSocketSession session) { sockets.put(session.getId(), session); }
    public void closed(String sessionId) { sockets.remove(sessionId); users.remove(sessionId); }

    @EventListener
    public void authenticated(SessionConnectEvent event) {
        var headers = StompHeaderAccessor.wrap(event.getMessage());
        if (headers.getUser() instanceof Authentication authentication
                && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            users.put(headers.getSessionId(), user);
        }
    }

    public void revoke(String userId) {
        users.forEach((id, user) -> { if (user.userId().equals(userId)) close(id); });
    }

    private void close(String id) {
        var socket = sockets.get(id);
        users.remove(id);
        if (socket != null) {
            try { socket.close(CloseStatus.POLICY_VIOLATION); }
            catch (java.io.IOException ignored) { /* Outbound authorization still rejects this session. */ }
        }
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var headers = SimpMessageHeaderAccessor.wrap(message);
        if (headers.getMessageType() != SimpMessageType.MESSAGE) return message;
        String id = headers.getSessionId();
        AuthenticatedUser user = id == null ? null : users.get(id);
        try {
            if (user == null || !sessionRegistry.findSessionId(user.userId()).map(user.sessionId()::equals).orElse(false)) {
                if (id != null) close(id);
                return null;
            }
            String destination = headers.getDestination();
            Object messageChannelId = headers.getHeader("messengerChannelId");
            if (messageChannelId instanceof Number number
                    && memberships.findActiveMembership(number.longValue(), user.userId()).isEmpty()) return null;
            if (destination != null && destination.startsWith("/topic/channel/")) {
                String channelId = destination.substring("/topic/channel/".length()).split("/")[0];
                if (memberships.findActiveMembership(Long.valueOf(channelId), user.userId()).isEmpty()) return null;
            }
            return message;
        } catch (RuntimeException unavailable) {
            return null;
        }
    }
}
