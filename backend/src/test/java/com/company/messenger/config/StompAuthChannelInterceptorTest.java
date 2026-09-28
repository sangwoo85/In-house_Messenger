package com.company.messenger.config;

import com.company.messenger.domain.channel.ChannelService;
import com.company.messenger.global.auth.AuthenticatedUser;
import com.company.messenger.global.auth.JwtTokenClaims;
import com.company.messenger.global.auth.JwtTokenProvider;
import com.company.messenger.global.auth.SessionRegistry;
import com.company.messenger.global.auth.TokenType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StompAuthChannelInterceptorTest {

    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final SessionRegistry sessionRegistry = mock(SessionRegistry.class);
    private final ChannelService channelService = mock(ChannelService.class);
    private final StompAuthChannelInterceptor interceptor =
            new StompAuthChannelInterceptor(jwtTokenProvider, sessionRegistry, channelService);

    @BeforeEach
    void setUp() {
        when(jwtTokenProvider.parseAndValidate("access-token", TokenType.ACCESS))
                .thenReturn(new JwtTokenClaims("user01", "session-1", TokenType.ACCESS));
        when(sessionRegistry.findSessionId("user01")).thenReturn(Optional.of("session-1"));
    }

    @Test
    void connectShouldRequireBearerToken() {
        assertThatThrownBy(() -> interceptor.preSend(message(StompCommand.CONNECT, null, null), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void connectShouldSetAuthenticatedUser() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer access-token");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        interceptor.preSend(message, null);

        UsernamePasswordAuthenticationToken authentication =
                (UsernamePasswordAuthenticationToken) accessor.getUser();
        assertThat(authentication.getPrincipal()).isEqualTo(new AuthenticatedUser("user01", "session-1"));
    }

    @Test
    void channelSubscriptionShouldCheckMembership() {
        interceptor.preSend(message(
                StompCommand.SUBSCRIBE,
                "/topic/channel/42/typing",
                authenticatedUser()
        ), null);

        verify(channelService).assertMembership(42L, "user01");
    }

    @Test
    void sendShouldRejectDirectBrokerDestination() {
        assertThatThrownBy(() -> interceptor.preSend(message(
                StompCommand.SEND,
                "/topic/channel/42",
                authenticatedUser()
        ), null)).isInstanceOf(AccessDeniedException.class);
    }

    private Message<byte[]> message(StompCommand command, String destination, java.security.Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        accessor.setUser(user);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private UsernamePasswordAuthenticationToken authenticatedUser() {
        return new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser("user01", "session-1"),
                null,
                AuthorityUtils.NO_AUTHORITIES
        );
    }
}
