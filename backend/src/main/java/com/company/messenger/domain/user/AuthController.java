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
    private final UserService userService;

    /** Accepts emprId and password and forwards authentication to the business API. */
    @PostMapping("/auth/login")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        return ApiResponse.ok(authService.login(request, response));
    }

    /** Renews a valid refresh-cookie session after external account validation. */
    @PostMapping("/auth/refresh")
    public ApiResponse<LoginResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        return ApiResponse.ok(authService.refresh(request, response));
    }

    /** Revokes this user session and removes the refresh cookie. */
    @PostMapping("/auth/logout")
    public ApiResponse<Void> logout(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletResponse response
    ) {
        authService.logout(authenticatedUser.userId(), response);
        return ApiResponse.ok(null);
    }

    /** Returns the signed-in user profile. */
    @GetMapping("/users/me")
    public ApiResponse<UserProfileResponse> me(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return ApiResponse.ok(authService.getMyProfile(authenticatedUser.userId()));
    }

    /** Lists current external users with live messenger availability. */
    @GetMapping("/users")
    public ApiResponse<java.util.List<UserProfileResponse>> getUsers(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return ApiResponse.ok(userService.getDirectory(authenticatedUser.userId()));
    }

    /** Returns heartbeat availability for the requested IDs. */
    @GetMapping("/users/presence")
    public ApiResponse<java.util.List<PresenceResponse>> getPresence(@RequestParam java.util.List<String> userIds) {
        return ApiResponse.ok(presenceService.getPresence(userIds));
    }

    /** Refreshes the signed-in user heartbeat while the app is running. */
    @PostMapping("/users/presence/heartbeat")
    public ApiResponse<Void> heartbeat(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody PresenceHeartbeatRequest request
    ) {
        presenceService.heartbeat(authenticatedUser.userId(), request.status());
        return ApiResponse.ok(null);
    }
}
