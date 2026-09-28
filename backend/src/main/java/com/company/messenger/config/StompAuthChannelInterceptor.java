package com.company.messenger.config;

import com.company.messenger.domain.channel.ChannelService;
import com.company.messenger.global.auth.AuthenticatedUser;
import com.company.messenger.global.auth.JwtTokenClaims;
import com.company.messenger.global.auth.JwtTokenProvider;
import com.company.messenger.global.auth.SessionRegistry;
import com.company.messenger.global.auth.TokenType;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String CHANNEL_TOPIC_PREFIX = "/topic/channel/";

    private final JwtTokenProvider jwtTokenProvider;
    private final SessionRegistry sessionRegistry;
    private final ChannelService channelService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (accessor.getCommand() == StompCommand.CONNECT) {
            authenticate(accessor);
            return message;
        }

        if (accessor.getCommand() != StompCommand.SEND && accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }

        AuthenticatedUser user = authenticatedUser(accessor);
        assertActiveSession(user);
        if (accessor.getCommand() == StompCommand.SEND) {
            authorizeSend(accessor.getDestination());
        } else {
            authorizeSubscription(accessor.getDestination(), user.userId());
        }

        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new AccessDeniedException("Authentication required");
        }

        JwtTokenClaims claims = jwtTokenProvider.parseAndValidate(authorization.substring(7), TokenType.ACCESS);
        AuthenticatedUser user = new AuthenticatedUser(claims.userId(), claims.sessionId());
        assertActiveSession(user);
        accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, AuthorityUtils.NO_AUTHORITIES));
    }

    private AuthenticatedUser authenticatedUser(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken authentication
                && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }

        throw new AccessDeniedException("Authentication required");
    }

    private void assertActiveSession(AuthenticatedUser user) {
        boolean active = sessionRegistry.findSessionId(user.userId())
                .map(user.sessionId()::equals)
                .orElse(false);
        if (!active) {
            throw new AccessDeniedException("Session expired");
        }
    }

    private void authorizeSubscription(String destination, String userId) {
        if (destination == null) {
            throw new AccessDeniedException("Destination required");
        }
        if (destination.equals("/topic/notice") || destination.startsWith("/user/queue/")) {
            return;
        }
        if (!destination.startsWith(CHANNEL_TOPIC_PREFIX)) {
            throw new AccessDeniedException("Subscription denied");
        }

        String channelPath = destination.substring(CHANNEL_TOPIC_PREFIX.length());
        String channelId = channelPath.endsWith("/typing")
                ? channelPath.substring(0, channelPath.length() - "/typing".length())
                : channelPath;
        try {
            channelService.assertMembership(Long.valueOf(channelId), userId);
        } catch (NumberFormatException exception) {
            throw new AccessDeniedException("Subscription denied", exception);
        }
    }

    private void authorizeSend(String destination) {
        if (!"/app/chat.send".equals(destination) && !"/app/chat.typing".equals(destination)) {
            throw new AccessDeniedException("Send destination denied");
        }
    }
}
