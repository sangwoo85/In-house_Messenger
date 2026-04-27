package com.company.messenger.domain.user;

public record LoginResponse(
        String accessToken,
        long expiresIn,
        UserProfileResponse user
) {
    public static LoginResponse of(User user, String accessToken, long expiresIn) {
        return new LoginResponse(accessToken, expiresIn, UserProfileResponse.from(user));
    }
}

