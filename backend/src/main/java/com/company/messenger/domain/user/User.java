package com.company.messenger.domain.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_login_at", nullable = false)
    private LocalDateTime lastLoginAt;

    @Builder
    private User(String userId, String nickname, String profileImageUrl, UserStatus status, LocalDateTime createdAt, LocalDateTime lastLoginAt) {
        this.userId = userId;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.status = status;
        this.createdAt = createdAt;
        this.lastLoginAt = lastLoginAt;
    }

    public static User create(String userId) {
        LocalDateTime now = LocalDateTime.now();
        return User.builder()
                .userId(userId)
                .nickname(userId)
                .profileImageUrl(null)
                .status(UserStatus.ONLINE)
                .createdAt(now)
                .lastLoginAt(now)
                .build();
    }

    public void markLoggedIn() {
        this.status = UserStatus.ONLINE;
        this.lastLoginAt = LocalDateTime.now();
    }

    public void markLoggedOut() {
        this.status = UserStatus.OFFLINE;
    }
}

