package com.company.messenger.domain.message;

import com.company.messenger.domain.channel.Channel;
import com.company.messenger.domain.file.FileAttachment;
import com.company.messenger.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_id", nullable = false)
    private Channel channel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private User sender;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id")
    private FileAttachment fileAttachment;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Message(
            Channel channel,
            User sender,
            String content,
            MessageType type,
            FileAttachment fileAttachment,
            boolean deleted,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.channel = channel;
        this.sender = sender;
        this.content = content;
        this.type = type;
        this.fileAttachment = fileAttachment;
        this.deleted = deleted;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Message create(Channel channel, User sender, String content, MessageType type, FileAttachment fileAttachment) {
        LocalDateTime now = LocalDateTime.now();
        return Message.builder()
                .channel(channel)
                .sender(sender)
                .content(content)
                .type(type)
                .fileAttachment(fileAttachment)
                .deleted(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
