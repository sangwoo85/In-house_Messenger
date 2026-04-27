package com.company.messenger.domain.user;

import com.company.messenger.global.auth.AuthenticatedUser;
import com.company.messenger.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PresenceService presenceService;

    @PostMapping("/auth/login")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        return ApiResponse.ok(authService.login(request, response));
    }

    @PostMapping("/auth/refresh")
    public ApiResponse<LoginResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        return ApiResponse.ok(authService.refresh(request, response));
    }

    @PostMapping("/auth/logout")
    public ApiResponse<Void> logout(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        authService.logout(authenticatedUser.userId(), request, response);
        return ApiResponse.ok(null);
    }

    @GetMapping("/users/me")
    public ApiResponse<UserProfileResponse> me(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return ApiResponse.ok(authService.getMyProfile(authenticatedUser.userId()));
    }

    @GetMapping("/users/presence")
    public ApiResponse<java.util.List<PresenceResponse>> getPresence(@RequestParam java.util.List<String> userIds) {
        return ApiResponse.ok(presenceService.getPresence(userIds));
    }

    @PostMapping("/users/presence/heartbeat")
    public ApiResponse<Void> heartbeat(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody PresenceHeartbeatRequest request
    ) {
        presenceService.heartbeat(authenticatedUser.userId(), request.status());
        return ApiResponse.ok(null);
    }
}
