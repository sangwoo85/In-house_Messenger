package com.company.messenger.domain.message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

/** Stores reactions using the unique message/user pair. */
public interface MessageReactionRepository extends JpaRepository<MessageReaction, Long> {
    Optional<MessageReaction> findByMessageIdAndUserUserId(Long messageId, String userId);
    void deleteByMessageId(Long messageId);

    @Query("select r from MessageReaction r join fetch r.user where r.message.id in :messageIds order by r.id")
    List<MessageReaction> findForMessages(List<Long> messageIds);
}
