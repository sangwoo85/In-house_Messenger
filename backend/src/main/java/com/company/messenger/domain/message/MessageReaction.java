package com.company.messenger.domain.message;

import com.company.messenger.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One selected emoji per user and message, also enforced by a database unique constraint. */
@Entity
@Table(name = "message_reactions", uniqueConstraints = @UniqueConstraint(name = "uq_message_reactor", columnNames = {"message_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageReaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false, length = 16)
    private String emoji;

    /** Creates the user's first reaction; later selections replace its emoji. */
    public MessageReaction(Message message, User user, String emoji) {
        this.message = message;
        this.user = user;
        this.emoji = emoji;
    }

    /** Replaces a selection instead of adding another reaction for the same user. */
    public void select(String emoji) { this.emoji = emoji; }
}
