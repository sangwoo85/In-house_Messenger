package com.company.messenger.domain.user;

import com.company.messenger.global.auth.*;
import com.company.messenger.global.exception.BusinessException;
import com.company.messenger.global.exception.ErrorCode;
import com.company.messenger.global.external.InternalAuthClient;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    public static final String REFRESH_COOKIE_NAME = "refreshToken";

    private final InternalAuthClient internalAuthClient;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final SessionRegistry sessionRegistry;
    private final RefreshTokenStore refreshTokenStore;
    private final SessionExpiryNotifier sessionExpiryNotifier;
    private final AuthProperties authProperties;
    private final PresenceService presenceService;
    private final com.company.messenger.config.RealtimeSessionGuard realtimeSessionGuard;

    /** Authenticates through the business API and replaces any previous messenger session. */
    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletResponse response) {
        var externalProfile = internalAuthClient.login(request.emprId(), request.password());
        User user = userService.loginWithProfile(externalProfile);
        String newSessionId = UUID.randomUUID().toString();

        sessionRegistry.findSessionId(user.getUserId())
                .filter(existing -> !existing.equals(newSessionId))
                .ifPresent(existing -> sessionExpiryNotifier.notifySessionExpired(user.getUserId()));

        sessionRegistry.save(user.getUserId(), newSessionId);
        realtimeSessionGuard.revoke(user.getUserId());

        String accessToken = jwtTokenProvider.createAccessToken(user.getUserId(), newSessionId);
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getUserId(), newSessionId);
        refreshTokenStore.save(user.getUserId(), refreshToken, authProperties.refreshTokenExpiration());
        presenceService.heartbeat(user.getUserId(), UserStatus.ONLINE);
        addRefreshCookie(response, refreshToken);

        return LoginResponse.of(user, accessToken, authProperties.accessTokenExpiration().toSeconds());
    }

    /** Renews access only while the external directory still contains the account. */
    @Transactional
    public LoginResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractRefreshToken(request);
        JwtTokenClaims claims = jwtTokenProvider.parseAndValidate(refreshToken, TokenType.REFRESH);

        String storedRefreshToken = refreshTokenStore.find(claims.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        if (!storedRefreshToken.equals(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        String currentSessionId = sessionRegistry.findSessionId(claims.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_EXPIRED));

        if (!currentSessionId.equals(claims.sessionId())) {
            throw new BusinessException(ErrorCode.SESSION_EXPIRED);
        }

        User user;
        try {
            user = userService.getOrCreateDirectoryUser(claims.userId());
        } catch (BusinessException exception) {
            if (exception.getErrorCode() != ErrorCode.USER_NOT_FOUND) throw exception;
            logout(claims.userId(), response);
            throw new BusinessException(ErrorCode.SESSION_EXPIRED);
        }
        String accessToken = jwtTokenProvider.createAccessToken(user.getUserId(), currentSessionId);
        addRefreshCookie(response, refreshToken);
        return LoginResponse.of(user, accessToken, authProperties.accessTokenExpiration().toSeconds());
    }

    /** Revokes refresh tokens, sockets and the live presence heartbeat. */
    @Transactional
    public void logout(String userId, HttpServletResponse response) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        refreshTokenStore.delete(userId);
        sessionRegistry.delete(userId);
        realtimeSessionGuard.revoke(userId);
        userService.markLoggedOut(userId);
        presenceService.markOffline(userId);
        expireRefreshCookie(response);
    }

    /** Returns the current local profile reference; passwords are not stored locally. */
    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(String userId) {
        User user = userService.getByUserId(userId);
        return UserProfileResponse.from(user);
    }

    private String extractRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        return Arrays.stream(cookies)
                .filter(cookie -> REFRESH_COOKIE_NAME.equals(cookie.getName()))
                .findFirst()
                .map(Cookie::getValue)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));
    }

    private void addRefreshCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(authProperties.cookieSecure())
                .path("/")
                .sameSite(authProperties.cookieSecure() ? "None" : "Strict")
                .maxAge(authProperties.refreshTokenExpiration())
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void expireRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(authProperties.cookieSecure())
                .path("/")
                .sameSite(authProperties.cookieSecure() ? "None" : "Strict")
                .maxAge(Duration.ZERO)
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }
}
