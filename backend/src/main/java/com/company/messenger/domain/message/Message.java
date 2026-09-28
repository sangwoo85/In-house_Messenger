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
@Table(name = "messages", uniqueConstraints = @UniqueConstraint(name = "uq_message_request", columnNames = {"sender_id", "client_request_id"}))
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

    @Column(name = "client_request_id", length = 36)
    private String clientRequestId;

    // A scalar reference keeps replies shallow and allows history to load quotes in one batch.
    @Column(name = "reply_to_message_id")
    private Long replyToMessageId;

    public void setReplyToMessageId(Long value) { this.replyToMessageId = value; }

    /** Saves the request key used to avoid duplicate messages on retries. */
    public void setClientRequestId(String value) { this.clientRequestId = value; }
    /** Removes private content while preserving the message position as a tombstone. */
    public void delete() { this.deleted = true; this.content = ""; this.fileAttachment = null; touch(); }

    /** Advances the response version when a read receipt or reaction changes. */
    public void touch() { this.updatedAt = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS); }

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

    /** Central constructor used by the message factory and persistence framework. */
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

    /** Creates a message without an implicit read receipt for its sender. */
    public static Message create(Channel channel, User sender, String content, MessageType type, FileAttachment fileAttachment) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
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
