package com.company.messenger.domain.message;

import com.company.messenger.domain.channel.ChannelService;
import com.company.messenger.global.auth.AuthenticatedUser;
import com.company.messenger.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/channels")
@RequiredArgsConstructor
public class MessageController {

    private final ChannelService channelService;
    private final ChatService chatService;

    /** Returns one bounded history page only to active channel members. */
    @GetMapping("/{channelId}/messages")
    public ApiResponse<MessageSliceResponse> getMessages(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long channelId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "30") int size
    ) {
        return ApiResponse.ok(channelService.getMessages(authenticatedUser.userId(), channelId, cursor, size));
    }

    /** Stores a message and acknowledges its idempotency key before the client clears its draft. */
    @PostMapping("/{channelId}/messages")
    public ApiResponse<MessageResponse> send(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long channelId, @Valid @RequestBody ChatMessageRequest request) {
        if (!channelId.equals(request.channelId())) throw new com.company.messenger.global.exception.BusinessException(com.company.messenger.global.exception.ErrorCode.INVALID_MESSAGE);
        return ApiResponse.ok(chatService.saveMessage(user.userId(), request));
    }

    /** Persists permanent read receipts and returns the remaining channel unread count. */
    @PatchMapping("/{channelId}/read")
    public ApiResponse<Long> markRead(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long channelId,
            @Valid @RequestBody ReadMessageRequest request
    ) {
        return ApiResponse.ok(channelService.markRead(authenticatedUser.userId(), channelId, request.messageId()));
    }
}
