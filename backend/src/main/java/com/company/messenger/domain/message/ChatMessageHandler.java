package com.company.messenger.domain.message;

import com.company.messenger.global.auth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatMessageHandler {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload ChatMessageRequest request, Authentication authentication) {
        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        MessageResponse message = chatService.saveMessage(principal.userId(), request);
        messagingTemplate.convertAndSend("/topic/channel/" + request.channelId(), message);
    }

    @MessageMapping("/chat.typing")
    public void typing(@Payload TypingIndicatorRequest request, Authentication authentication) {
        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        TypingEventResponse event = chatService.createTypingEvent(principal.userId(), request);
        messagingTemplate.convertAndSend("/topic/channel/" + request.channelId() + "/typing", event);
    }
}
