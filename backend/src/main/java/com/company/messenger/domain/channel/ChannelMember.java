package com.company.messenger.domain.channel;

import com.company.messenger.domain.message.Message;
import com.company.messenger.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "channel_members",
        uniqueConstraints = @UniqueConstraint(name = "uq_channel_user", columnNames = {"channel_id", "user_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChannelMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_id", nullable = false)
    private Channel channel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChannelRole role;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_read_message_id")
    private Message lastReadMessage;

    @Builder
    private ChannelMember(Channel channel, User user, ChannelRole role, LocalDateTime joinedAt, LocalDateTime leftAt, Message lastReadMessage) {
        this.channel = channel;
        this.user = user;
        this.role = role;
        this.joinedAt = joinedAt;
        this.leftAt = leftAt;
        this.lastReadMessage = lastReadMessage;
    }

    public static ChannelMember join(Channel channel, User user, ChannelRole role) {
        return ChannelMember.builder()
                .channel(channel)
                .user(user)
                .role(role)
                .joinedAt(LocalDateTime.now())
                .build();
    }

    public void markRead(Message message) {
        this.lastReadMessage = message;
    }
}
