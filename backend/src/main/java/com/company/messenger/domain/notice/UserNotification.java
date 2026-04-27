package com.company.messenger.domain.notice;

import com.company.messenger.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "link_url", length = 500)
    private String linkUrl;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private UserNotification(User user, String title, String content, String linkUrl, boolean read, LocalDateTime createdAt) {
        this.user = user;
        this.title = title;
        this.content = content;
        this.linkUrl = linkUrl;
        this.read = read;
        this.createdAt = createdAt;
    }

    public static UserNotification create(User user, String title, String content, String linkUrl) {
        return UserNotification.builder()
                .user(user)
                .title(title)
                .content(content)
                .linkUrl(linkUrl)
                .read(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public void markRead() {
        this.read = true;
    }
}

