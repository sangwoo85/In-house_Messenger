package com.company.messenger.domain.notice;

import com.company.messenger.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal")
@RequiredArgsConstructor
public class InternalNoticeController {

    private final InternalApiGuard internalApiGuard;
    private final NoticeService noticeService;

    /** Accepts a company notice only after the internal API key is verified. */
    @PostMapping("/notice/broadcast")
    public ApiResponse<NoticeResponse> broadcast(
            @RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey,
            @Valid @RequestBody BroadcastNoticeRequest request
    ) {
        internalApiGuard.verify(apiKey);
        return ApiResponse.ok(noticeService.broadcast(request));
    }

    /** Stores personal business notifications regardless of the requested desktop mode. */
    @PostMapping("/notify/user")
    public ApiResponse<UserNotificationResponse> notifyUser(
            @RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey,
            @Valid @RequestBody NotifyUserRequest request
    ) {
        internalApiGuard.verify(apiKey);
        return ApiResponse.ok(noticeService.notifyUser(request));
    }
}
