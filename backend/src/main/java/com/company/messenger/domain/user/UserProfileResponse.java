package com.company.messenger.domain.user;

public record UserProfileResponse(
        Long id,
        String userId,
        String nickname,
        String profileImageUrl,
        UserStatus status
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUserId(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getStatus()
        );
    }
}

