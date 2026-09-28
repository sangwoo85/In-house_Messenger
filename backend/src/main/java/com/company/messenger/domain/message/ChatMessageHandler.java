package com.company.messenger.domain.message;

import com.company.messenger.global.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatMessageHandler {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.send")
    public void sendMessage(@Valid @Payload ChatMessageRequest request, Authentication authentication) {
        AuthenticatedUser principal = authenticatedUser(authentication);
        chatService.saveMessage(principal.userId(), request);
    }

    @MessageMapping("/chat.typing")
    public void typing(@Valid @Payload TypingIndicatorRequest request, Authentication authentication) {
        AuthenticatedUser principal = authenticatedUser(authentication);
        TypingEventResponse event = chatService.createTypingEvent(principal.userId(), request);
        messagingTemplate.convertAndSend("/topic/channel/" + request.channelId() + "/typing", event);
    }

    private AuthenticatedUser authenticatedUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            throw new AccessDeniedException("Authentication required");
        }

        return principal;
    }
}
