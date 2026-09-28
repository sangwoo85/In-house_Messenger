package com.company.messenger.domain.message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

/** Reads immutable receipts independently of current channel memberships. */
public interface MessageReadReceiptRepository extends JpaRepository<MessageReadReceipt, Long> {
    boolean existsByMessageId(Long messageId);
    boolean existsByMessageIdAndUserUserId(Long messageId, String userId);

    @Query("select r from MessageReadReceipt r join fetch r.user where r.message.id in :messageIds order by r.id")
    List<MessageReadReceipt> findForMessages(List<Long> messageIds);
}
