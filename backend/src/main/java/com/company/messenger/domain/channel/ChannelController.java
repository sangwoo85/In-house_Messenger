package com.company.messenger.domain.channel;

import com.company.messenger.global.auth.AuthenticatedUser;
import com.company.messenger.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Exposes channel membership operations using the authenticated employee identity. */
@RestController
@RequestMapping("/api/v1/channels")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;

    /** Lists only channels the signed-in user currently belongs to, with unread totals. */
    @GetMapping
    public ApiResponse<List<ChannelResponse>> getChannels(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return ApiResponse.ok(channelService.getChannels(authenticatedUser.userId()));
    }

    /** Creates a directory-verified group or reuses the same two users’ existing direct chat. */
    @PostMapping
    public ApiResponse<ChannelResponse> createChannel(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CreateChannelRequest request
    ) {
        return ApiResponse.ok(channelService.createChannel(authenticatedUser.userId(), request));
    }
    /** Lets the group owner add company directory users; ordinary members cannot invite. */
    @PostMapping("/{channelId}/members")
    public ApiResponse<ChannelResponse> invite(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long channelId, @Valid @RequestBody InviteMembersRequest request) {
        return ApiResponse.ok(channelService.invite(user.userId(), channelId, request));
    }

    /** Lets users leave a group or lets its owner remove another member; ownership transfers on departure. */
    @DeleteMapping("/{channelId}/members/{userId}")
    public ApiResponse<Void> remove(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long channelId, @PathVariable String userId) {
        channelService.removeMember(user.userId(), channelId, userId);
        return ApiResponse.ok(null);
    }
}

