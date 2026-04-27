package com.company.messenger.domain.channel;

import com.company.messenger.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "channels")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChannelType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private Channel(String name, ChannelType type, User createdBy, LocalDateTime createdAt) {
        this.name = name;
        this.type = type;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public static Channel create(String name, ChannelType type, User createdBy) {
        return Channel.builder()
                .name(name)
                .type(type)
                .createdBy(createdBy)
                .createdAt(LocalDateTime.now())
                .build();
    }
}

