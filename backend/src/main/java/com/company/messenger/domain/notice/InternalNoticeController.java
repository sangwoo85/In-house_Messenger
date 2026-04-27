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

    @PostMapping("/notice/broadcast")
    public ApiResponse<NoticeResponse> broadcast(
            @RequestHeader("X-Internal-Api-Key") String apiKey,
            @Valid @RequestBody BroadcastNoticeRequest request
    ) {
        internalApiGuard.verify(apiKey);
        return ApiResponse.ok(noticeService.broadcast(request));
    }

    @PostMapping("/notify/user")
    public ApiResponse<UserNotificationResponse> notifyUser(
            @RequestHeader("X-Internal-Api-Key") String apiKey,
            @Valid @RequestBody NotifyUserRequest request
    ) {
        internalApiGuard.verify(apiKey);
        return ApiResponse.ok(noticeService.notifyUser(request));
    }
}

