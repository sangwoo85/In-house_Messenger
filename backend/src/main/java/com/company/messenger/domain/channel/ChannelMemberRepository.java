package com.company.messenger.domain.channel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/** Queries active memberships separately from historical rows retained after departure. */
public interface ChannelMemberRepository extends JpaRepository<ChannelMember, Long> {

    /** Includes departed members so an invitation can reuse their unique membership row. */
    Optional<ChannelMember> findByChannelIdAndUserUserId(Long channelId, String userId);

    /** Locks an active membership after its channel is locked, protecting the unread cursor. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select cm from ChannelMember cm where cm.channel.id = :channelId and cm.user.userId = :userId and cm.leftAt is null")
    Optional<ChannelMember> findMembershipForUpdate(Long channelId, String userId);

    /** Loads the current user’s active conversations in newest-channel order. */
    @Query("""
            select cm
            from ChannelMember cm
            join fetch cm.channel c
            where cm.user.userId = :userId
              and cm.leftAt is null
            order by c.createdAt desc
            """)
    List<ChannelMember> findActiveByUserId(String userId);

    /** Resolves authorization from an active membership; departed users no longer have access. */
    @Query("""
            select cm
            from ChannelMember cm
            where cm.channel.id = :channelId
              and cm.user.userId = :userId
              and cm.leftAt is null
            """)
    Optional<ChannelMember> findActiveMembership(Long channelId, String userId);

    /** Loads current participants with their identities for metadata and authorized event delivery. */
    @Query("""
            select cm
            from ChannelMember cm
            join fetch cm.user u
            where cm.channel.id = :channelId
              and cm.leftAt is null
            order by u.userId asc
            """)
    List<ChannelMember> findActiveMembers(Long channelId);
}

