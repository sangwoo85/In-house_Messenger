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

    @GetMapping("/{channelId}/messages")
    public ApiResponse<MessageSliceResponse> getMessages(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long channelId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "30") int size
    ) {
        return ApiResponse.ok(channelService.getMessages(authenticatedUser.userId(), channelId, cursor, size));
    }

    @PatchMapping("/{channelId}/read")
    public ApiResponse<Void> markRead(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long channelId,
            @Valid @RequestBody ReadMessageRequest request
    ) {
        channelService.markRead(authenticatedUser.userId(), channelId, request.messageId());
        return ApiResponse.ok(null);
    }
}
