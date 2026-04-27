package com.company.messenger.domain.channel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ChannelMemberRepository extends JpaRepository<ChannelMember, Long> {

    @Query("""
            select cm
            from ChannelMember cm
            join fetch cm.channel c
            where cm.user.userId = :userId
              and cm.leftAt is null
            order by c.createdAt desc
            """)
    List<ChannelMember> findActiveByUserId(String userId);

    @Query("""
            select cm
            from ChannelMember cm
            where cm.channel.id = :channelId
              and cm.user.userId = :userId
              and cm.leftAt is null
            """)
    Optional<ChannelMember> findActiveMembership(Long channelId, String userId);

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

