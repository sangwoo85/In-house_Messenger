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

    @Column(name = "notification_type", nullable = false, length = 50)
    private String notificationType;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(name = "display_mode", nullable = false, length = 20)
    private NotificationDisplayMode displayMode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Initializes a notification entity with explicit presentation defaults. */
    @Builder
    private UserNotification(User user, String title, String content, String linkUrl, boolean read, LocalDateTime createdAt, String notificationType, NotificationDisplayMode displayMode) {
        this.user = user;
        this.title = title;
        this.content = content;
        this.linkUrl = linkUrl;
        this.read = read;
        this.createdAt = createdAt;
        this.notificationType = notificationType == null ? "GENERAL" : notificationType;
        this.displayMode = displayMode == null ? NotificationDisplayMode.SYSTEM : displayMode;
    }

    /** Creates an ordinary personal notification for existing callers. */
    public static UserNotification create(User user, String title, String content, String linkUrl) {
        return create(user, title, content, linkUrl, "GENERAL", NotificationDisplayMode.SYSTEM);
    }

    /** Saves every delivery mode, including silent notifications, in the user's history. */
    public static UserNotification create(User user, String title, String content, String linkUrl,
                                          String notificationType, NotificationDisplayMode displayMode) {
        return UserNotification.builder()
                .user(user)
                .title(title)
                .content(content)
                .linkUrl(linkUrl)
                .read(false)
                .notificationType(notificationType)
                .displayMode(displayMode)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /** Marks only the read state; delivery options and notification content remain unchanged. */
    public void markRead() {
        this.read = true;
    }
}

