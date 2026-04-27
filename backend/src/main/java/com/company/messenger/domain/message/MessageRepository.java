package com.company.messenger.domain.message;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("""
            select m
            from Message m
            join fetch m.sender s
            left join fetch m.fileAttachment f
            where m.channel.id = :channelId
              and (:cursor is null or m.id < :cursor)
            order by m.id desc
            """)
    List<Message> findMessages(Long channelId, Long cursor, Pageable pageable);
}
