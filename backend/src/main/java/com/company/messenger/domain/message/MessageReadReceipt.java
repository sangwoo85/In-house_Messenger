package com.company.messenger.domain.message;

import com.company.messenger.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/** A permanent record that another user read a message; leaving a channel does not erase it. */
@Entity
@Table(name = "message_read_receipts", uniqueConstraints = @UniqueConstraint(name = "uq_message_reader", columnNames = {"message_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageReadReceipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "read_at", nullable = false)
    private LocalDateTime readAt;

    /** Records the first read only; callers hold the channel lock before creating this row. */
    public MessageReadReceipt(Message message, User user) {
        this.message = message;
        this.user = user;
        this.readAt = LocalDateTime.now();
    }
}
