package com.company.messenger.domain.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Directory refresh and image changes update independent fields even when their transactions overlap.
@org.hibernate.annotations.DynamicUpdate
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true, length = 50)
    private String userId;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(length = 100)
    private String department;

    @Column(name = "department_id", length = 100)
    private String departmentId;

    @Column(name = "profile_image_key", length = 36)
    private String profileImageKey;

    @Column(name = "user_group", length = 100)
    private String userGroup;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_login_at", nullable = false)
    private LocalDateTime lastLoginAt;

    @Builder
    private User(String userId, String nickname, String profileImageUrl, String department, String userGroup, UserStatus status, LocalDateTime createdAt, LocalDateTime lastLoginAt) {
        this.userId = userId;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.department = department;
        this.userGroup = userGroup;
        this.status = status;
        this.createdAt = createdAt;
        this.lastLoginAt = lastLoginAt;
    }

    /** Creates a local identity reference for internal tests and service callers. */
    public static User create(String userId) {
        return createLoggedIn(userId, userId, null, null, null);
    }

    /** Creates an authenticated profile reference without credentials. */
    public static User createLoggedIn(String userId, String nickname, String profileImageUrl, String department, String userGroup) {
        LocalDateTime now = LocalDateTime.now();
        return User.builder()
                .userId(userId)
                .nickname(nickname)
                .profileImageUrl(profileImageUrl)
                .department(department)
                .userGroup(userGroup)
                .status(UserStatus.ONLINE)
                .createdAt(now)
                .lastLoginAt(now)
                .build();
    }

    /** Creates an offline identity reference obtained from the external directory. */
    public static User createDirectoryUser(String userId, String nickname, String profileImageUrl, String department, String userGroup) {
        LocalDateTime now = LocalDateTime.now();
        return User.builder()
                .userId(userId)
                .nickname(nickname)
                .profileImageUrl(profileImageUrl)
                .department(department)
                .userGroup(userGroup)
                .status(UserStatus.OFFLINE)
                .createdAt(now)
                .lastLoginAt(now)
                .build();
    }

    /** Records the most recent successful external login. */
    public void markLoggedIn() {
        this.status = UserStatus.ONLINE;
        this.lastLoginAt = LocalDateTime.now();
    }

    /** Refreshes business-owned profile fields while preserving the local image. */
    public void syncProfile(String nickname, String profileImageUrl, String department, String userGroup) {
        this.nickname = nickname;
        // Local profile images survive external directory refreshes.
        this.department = department;
        this.userGroup = userGroup;
    }

    /** Updates the business department ID used to place this user in the organization tree. */
    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    /** Stores only a server-generated image key and an authenticated messenger URL. */
    public void setProfileImageKey(String imageKey) {
        this.profileImageKey = imageKey;
        this.profileImageUrl = imageKey == null ? null : "/api/v1/users/"
                + org.springframework.web.util.UriUtils.encodePathSegment(userId, java.nio.charset.StandardCharsets.UTF_8)
                + "/profile-image?v=" + imageKey;
    }

    /** Records explicit logout; live availability is read from the Redis heartbeat. */
    public void markLoggedOut() {
        this.status = UserStatus.OFFLINE;
    }
}
