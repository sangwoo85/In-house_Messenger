package com.company.messenger.domain.channel;

import com.company.messenger.global.auth.AuthenticatedUser;
import com.company.messenger.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/channels")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;

    @GetMapping
    public ApiResponse<List<ChannelResponse>> getChannels(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return ApiResponse.ok(channelService.getChannels(authenticatedUser.userId()));
    }

    @PostMapping
    public ApiResponse<ChannelResponse> createChannel(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CreateChannelRequest request
    ) {
        return ApiResponse.ok(channelService.createChannel(authenticatedUser.userId(), request));
    }
}

