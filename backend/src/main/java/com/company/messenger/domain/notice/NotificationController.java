package com.company.messenger.domain.notice;

import com.company.messenger.global.auth.AuthenticatedUser;
import com.company.messenger.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NoticeService noticeService;

    @GetMapping
    public ApiResponse<UserNotificationPageResponse> getNotifications(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(noticeService.getNotifications(authenticatedUser.userId(), page, size));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markRead(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long id
    ) {
        noticeService.markNotificationRead(authenticatedUser.userId(), id);
        return ApiResponse.ok(null);
    }
}
