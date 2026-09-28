package com.company.messenger.domain.user;

/** Profile shared by login, user directory and organization views. */
public record UserProfileResponse(Long id, String userId, String nickname, String profileImageUrl,
                                  String department, String userGroup, UserStatus status, String departmentId) {
    /** Builds a response using the caller's live presence result. */
    public static UserProfileResponse from(User user, UserStatus status) {
        return new UserProfileResponse(user.getId(), user.getUserId(), user.getNickname(), user.getProfileImageUrl(),
                user.getDepartment(), user.getUserGroup(), status, user.getDepartmentId());
    }

    /** Builds a response immediately after login or a local profile change. */
    public static UserProfileResponse from(User user) {
        return from(user, user.getStatus());
    }
}
