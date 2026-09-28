package com.company.messenger.domain.channel;

import org.springframework.data.jpa.repository.JpaRepository;

/** Looks up direct conversations and serializes channel mutations. */
public interface ChannelRepository extends JpaRepository<Channel, Long> {
    /** Finds the same two-person conversation regardless of which participant created it. */
    java.util.Optional<Channel> findByDirectKey(String directKey);

    /** Acquires the first lock used by message, read, and membership writes to avoid competing changes. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Channel c where c.id = :id")
    java.util.Optional<Channel> findForUpdate(Long id);
}

