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

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private Notice(String title, String content, String sender, LocalDateTime createdAt) {
        this.title = title;
        this.content = content;
        this.sender = sender;
        this.createdAt = createdAt;
    }

    public static Notice create(String title, String content, String sender) {
        return Notice.builder()
                .title(title)
                .content(content)
                .sender(sender)
                .createdAt(LocalDateTime.now())
                .build();
    }
}

