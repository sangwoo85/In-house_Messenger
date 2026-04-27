package com.company.messenger.config;

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
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final SessionRegistry sessionRegistry;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return message;
        }

        JwtTokenClaims claims = jwtTokenProvider.parseAndValidate(authorization.substring(7), TokenType.ACCESS);
        boolean validSession = sessionRegistry.findSessionId(claims.userId())
                .map(claims.sessionId()::equals)
                .orElse(false);

        if (validSession) {
            AuthenticatedUser user = new AuthenticatedUser(claims.userId(), claims.sessionId());
            accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, AuthorityUtils.NO_AUTHORITIES));
        }

        return message;
    }
}

