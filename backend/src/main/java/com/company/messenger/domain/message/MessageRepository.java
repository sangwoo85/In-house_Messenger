package com.company.messenger.domain.message;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("""
            select m from Message m
            left join fetch m.sender
            left join fetch m.fileAttachment
            where m.channel.id = :channelId and m.id in :ids
            """)
    List<Message> findReplyTargets(Long channelId, List<Long> ids);

    /** Aggregates the original send time for a complete channel list in one query, excluding no message types. */
    @Query("""
            select new com.company.messenger.domain.message.ChannelLastMessageTime(m.channel.id, max(m.createdAt))
            from Message m where m.channel.id in :channelIds group by m.channel.id
            """)
    List<ChannelLastMessageTime> findLastMessageTimes(List<Long> channelIds);


    /** Looks up the channel before taking locks, keeping every mutation in channel-then-message order. */
    @Query("select m.channel.id from Message m where m.id = :id")
    java.util.Optional<Long> findChannelId(Long id);

    /** Returns messages not yet read by this user, including history shown after joining a channel. */
    @Query("""
            select m from Message m where m.channel.id = :channelId and m.id <= :throughId
              and m.deleted = false and m.sender.userId <> :userId
              and not exists (select r.id from MessageReadReceipt r where r.message = m and r.user.userId = :userId)
            order by m.id
            """)
    List<Message> findUnreadReceipts(Long channelId, String userId, Long throughId, Pageable page);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Message m where m.id = :id")
    java.util.Optional<Message> findForUpdate(Long id);

    java.util.Optional<Message> findBySenderUserIdAndClientRequestId(String userId, String clientRequestId);

    @Query("select count(m) from Message m where m.channel.id = :channelId and m.id > :lastReadId and m.createdAt >= :joinedAt and m.sender.userId <> :userId and m.deleted = false and not exists (select r.id from MessageReadReceipt r where r.message = m and r.user.userId = :userId)")
    long countUnread(Long channelId, String userId, Long lastReadId, java.time.LocalDateTime joinedAt);

    @Query("select count(m) from Message m, ChannelMember cm where m.fileAttachment.id = :fileId and m.deleted = false and cm.channel = m.channel and cm.user.userId = :userId and cm.leftAt is null")
    long countAccessibleAttachment(Long fileId, String userId);

    @Query("""
            select m
            from Message m
            left join fetch m.sender s
            left join fetch m.fileAttachment f
            where m.channel.id = :channelId
              and (:cursor is null or m.id < :cursor)
            order by m.id desc
            """)
    List<Message> findMessages(Long channelId, Long cursor, Pageable pageable);
}
