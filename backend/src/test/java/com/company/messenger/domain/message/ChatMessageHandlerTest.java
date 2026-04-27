package com.company.messenger.domain.message;

import com.company.messenger.global.auth.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ChatMessageHandlerTest {

    @Test
    void sendMessageShouldBroadcastToChannelTopic() {
        ChatService chatService = mock(ChatService.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        ChatMessageHandler handler = new ChatMessageHandler(chatService, messagingTemplate);
        ChatMessageRequest request = new ChatMessageRequest(10L, "hello", MessageType.TEXT, null);
        MessageResponse response = new MessageResponse(1L, 10L, "user01", "hello", MessageType.TEXT, null, java.time.LocalDateTime.now(), false);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser("user01", "session-1"),
                null,
                AuthorityUtils.NO_AUTHORITIES
        );

        when(chatService.saveMessage("user01", request)).thenReturn(response);

        handler.sendMessage(request, authentication);

        verify(chatService).saveMessage("user01", request);
        verify(messagingTemplate).convertAndSend(eq("/topic/channel/10"), eq(response));
    }

    @Test
    void typingShouldBroadcastTypingTopic() {
        ChatService chatService = mock(ChatService.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        ChatMessageHandler handler = new ChatMessageHandler(chatService, messagingTemplate);
        TypingIndicatorRequest request = new TypingIndicatorRequest(10L, true);
        TypingEventResponse response = new TypingEventResponse(10L, "user01", true);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser("user01", "session-1"),
                null,
                AuthorityUtils.NO_AUTHORITIES
        );

        when(chatService.createTypingEvent("user01", request)).thenReturn(response);

        handler.typing(request, authentication);

        verify(chatService).createTypingEvent("user01", request);
        verify(messagingTemplate).convertAndSend(eq("/topic/channel/10/typing"), eq(response));
    }
}
