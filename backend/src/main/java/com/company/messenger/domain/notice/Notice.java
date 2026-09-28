package com.company.messenger.domain.notice;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, length = 100)
    private String sender;

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
    private Notice(String title, String content, String sender, LocalDateTime createdAt, String notificationType, NotificationDisplayMode displayMode) {
        this.title = title;
        this.content = content;
        this.sender = sender;
        this.createdAt = createdAt;
        this.notificationType = notificationType == null ? "GENERAL" : notificationType;
        this.displayMode = displayMode == null ? NotificationDisplayMode.SYSTEM : displayMode;
    }

    /** Creates an ordinary company notice for existing callers. */
    public static Notice create(String title, String content, String sender) {
        return create(title, content, sender, "GENERAL", NotificationDisplayMode.SYSTEM);
    }

    /** Saves display options with the notice so history and live events agree. */
    public static Notice create(String title, String content, String sender,
                                String notificationType, NotificationDisplayMode displayMode) {
        return Notice.builder()
                .title(title)
                .content(content)
                .sender(sender)
                .notificationType(notificationType)
                .displayMode(displayMode)
                .createdAt(LocalDateTime.now())
                .build();
    }
}

