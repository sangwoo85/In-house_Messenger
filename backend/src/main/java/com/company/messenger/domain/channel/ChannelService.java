package com.company.messenger.domain.channel;

import com.company.messenger.domain.message.*;
import com.company.messenger.domain.user.User;
import com.company.messenger.domain.user.UserService;
import com.company.messenger.global.exception.*;
import com.company.messenger.global.response.RealtimeEvents;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

/** Coordinates directory-verified channel membership, history access, and durable read state. */
@Service
@RequiredArgsConstructor
public class ChannelService {
    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final MessageRepository messageRepository;
    private final UserService userService;
    private final RealtimeEvents events;
    private final MessageReadReceiptRepository receipts;
    private final MessageDetailsService details;

    /** Creates a direct or group channel from users verified by the company directory API. */
    @Transactional
    public ChannelResponse createChannel(String ownerUserId, CreateChannelRequest request) {
        LinkedHashSet<String> ids = new LinkedHashSet<>(request.memberUserIds());
        ids.add(ownerUserId);
        validateChannel(request, ids);
        List<User> users = userService.resolveDirectoryUsers(new ArrayList<>(ids));
        User owner = users.stream().filter(user -> user.getUserId().equals(ownerUserId)).findFirst().orElseThrow();
        String directKey = request.type() == ChannelType.DM ? directKey(ids) : null;
        if (directKey != null) {
            var existing = channelRepository.findByDirectKey(directKey);
            if (existing.isPresent()) return toResponse(existing.get(), ownerUserId);
            // Preserve pre-migration DMs whose canonical key has not been assigned yet.
            for (var membership : channelMemberRepository.findActiveByUserId(ownerUserId)) {
                var channel = membership.getChannel();
                if (channel.getType() == ChannelType.DM && new HashSet<>(getActiveMemberUserIds(channel.getId())).equals(ids)) {
                    channel.setDirectKey(directKey);
                    return toResponse(channel, ownerUserId);
                }
            }
        }
        Channel channel = Channel.create(request.name(), request.type(), owner);
        channel.setDirectKey(directKey);
        channelRepository.save(channel);
        channelMemberRepository.saveAll(users.stream().map(user -> ChannelMember.join(channel, user,
                user.getUserId().equals(ownerUserId) ? ChannelRole.OWNER : ChannelRole.MEMBER)).toList());
        channelMemberRepository.flush();
        changed(channel.getId(), ids, "MEMBERSHIP");
        return toResponse(channel, ownerUserId, null);
    }

    /** Lists active channels with the database-backed unread total for the current user. */
    @Transactional(readOnly = true)
    public List<ChannelResponse> getChannels(String userId) {
        var memberships = channelMemberRepository.findActiveByUserId(userId);
        var channelIds = memberships.stream().map(member -> member.getChannel().getId()).toList();
        var lastMessageTimes = lastMessageTimes(channelIds);
        return memberships.stream().map(member -> toResponse(member.getChannel(), userId,
                lastMessageTimes.get(member.getChannel().getId()))).toList();
    }

    /** Loads a chronological history page with permanent read receipts and emoji reactions. */
    @Transactional(readOnly = true)
    public MessageSliceResponse getMessages(String userId, Long channelId, Long cursor, int size) {
        assertMembership(channelId, userId);
        if (size < 1 || size > 100) throw new BusinessException(ErrorCode.INVALID_PAGE_REQUEST);
        var rows = messageRepository.findMessages(channelId, cursor, PageRequest.of(0, size + 1));
        boolean hasNext = rows.size() > size;
        var page = hasNext ? rows.subList(0, size) : rows;
        var items = details.responses(page).stream().sorted(Comparator.comparing(MessageResponse::id)).toList();
        return new MessageSliceResponse(items, hasNext ? page.getLast().getId() : null, hasNext);
    }

    /** Rejects access unless the user currently belongs to this channel. */
    @Transactional(readOnly = true)
    public void assertMembership(Long channelId, String userId) { membership(channelId, userId); }

    /** Serializes with deletion, stores permanent read receipts, and advances the read cursor monotonically. */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public long markRead(String userId, Long channelId, Long messageId) {
        channelRepository.findForUpdate(channelId).orElseThrow(() -> new BusinessException(ErrorCode.CHANNEL_NOT_FOUND));
        var member = channelMemberRepository.findMembershipForUpdate(channelId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHANNEL_ACCESS_DENIED));
        var message = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));
        if (!message.getChannel().getId().equals(channelId)) throw new BusinessException(ErrorCode.MESSAGE_NOT_FOUND);
        // Small batches prevent a first read of a long history from producing an oversized SQL IN clause.
        var batch = messageRepository.findUnreadReceipts(channelId, userId, messageId, PageRequest.of(0, 250));
        while (!batch.isEmpty()) {
            receipts.saveAll(batch.stream().map(item -> new MessageReadReceipt(item, member.getUser())).toList());
            batch.forEach(Message::touch);
            details.publishReadUpdates(batch);
            batch = messageRepository.findUnreadReceipts(channelId, userId, messageId, PageRequest.of(0, 250));
        }
        member.markRead(message);
        long remaining = unread(member);
        changed(channelId, List.of(userId), "READ");
        return remaining;
    }

    /** Allows the group owner to invite directory users or restore a previous membership. */
    @Transactional
    public ChannelResponse invite(String actor, Long channelId, InviteMembersRequest request) {
        Channel channel = lockedGroup(channelId);
        if (membership(channelId, actor).getRole() != ChannelRole.OWNER) throw new BusinessException(ErrorCode.CHANNEL_MANAGEMENT_DENIED);
        var current = getActiveMemberUserIds(channelId);
        var combined = new HashSet<>(current);
        combined.addAll(request.memberUserIds());
        if (combined.size() > 100) throw new BusinessException(ErrorCode.INVALID_CHANNEL);
        var users = userService.resolveDirectoryUsers(request.memberUserIds());
        for (User user : users) {
            var existing = channelMemberRepository.findByChannelIdAndUserUserId(channelId, user.getUserId());
            if (existing.isEmpty()) channelMemberRepository.save(ChannelMember.join(channel, user, ChannelRole.MEMBER));
            else if (existing.get().getLeftAt() != null) existing.get().rejoin();
        }
        channelMemberRepository.flush();
        changed(channelId, combined, "MEMBERSHIP");
        return toResponse(channel, actor);
    }

    /** Removes a member and transfers ownership when necessary; historical read receipts remain intact. */
    @Transactional
    public void removeMember(String actor, Long channelId, String targetUserId) {
        lockedGroup(channelId);
        var actorMember = membership(channelId, actor);
        if (!actor.equals(targetUserId) && actorMember.getRole() != ChannelRole.OWNER) throw new BusinessException(ErrorCode.CHANNEL_MANAGEMENT_DENIED);
        var target = membership(channelId, targetUserId);
        var recipients = getActiveMemberUserIds(channelId);
        target.leave();
        if (target.getRole() == ChannelRole.OWNER) {
            channelMemberRepository.findActiveMembers(channelId).stream()
                    .filter(member -> !member.getUser().getUserId().equals(targetUserId))
                    .min(Comparator.comparing(ChannelMember::getJoinedAt)).ifPresent(ChannelMember::promote);
        }
        changed(channelId, recipients, "MEMBERSHIP");
    }

    /** Returns current recipients for a channel event. */
    @Transactional(readOnly = true)
    public List<String> getActiveMemberUserIds(Long channelId) {
        return channelMemberRepository.findActiveMembers(channelId).stream().map(member -> member.getUser().getUserId()).toList();
    }

    /** Finds an active membership or reports a forbidden channel. */
    private ChannelMember membership(Long channelId, String userId) {
        return channelMemberRepository.findActiveMembership(channelId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHANNEL_ACCESS_DENIED));
    }

    /** Locks a group before changing its membership. */
    private Channel lockedGroup(Long id) {
        var channel = channelRepository.findForUpdate(id).orElseThrow(() -> new BusinessException(ErrorCode.CHANNEL_NOT_FOUND));
        if (channel.getType() != ChannelType.GROUP) throw new BusinessException(ErrorCode.CHANNEL_MANAGEMENT_DENIED);
        return channel;
    }

    /** Counts messages after the membership cursor, excluding the user’s own messages. */
    private long unread(ChannelMember member) {
        return messageRepository.countUnread(member.getChannel().getId(), member.getUser().getUserId(),
                member.getLastReadMessage() == null ? 0L : member.getLastReadMessage().getId(), member.getJoinedAt());
    }

    /** Loads latest send times together; empty channels intentionally have no entry or fallback date. */
    private Map<Long, LocalDateTime> lastMessageTimes(List<Long> channelIds) {
        if (channelIds.isEmpty()) return Map.of();
        return messageRepository.findLastMessageTimes(channelIds).stream().collect(Collectors.toMap(
                ChannelLastMessageTime::channelId, ChannelLastMessageTime::lastMessageAt));
    }

    /** Includes existing history when an invitation or a reused direct chat returns one channel. */
    private ChannelResponse toResponse(Channel channel, String viewer) {
        return toResponse(channel, viewer, lastMessageTimes(List.of(channel.getId())).get(channel.getId()));
    }

    /** Builds channel metadata with a preloaded send time, avoiding one extra query per listed channel. */
    private ChannelResponse toResponse(Channel channel, String viewer, LocalDateTime lastMessageAt) {
        var members = channelMemberRepository.findActiveMembers(channel.getId());
        var member = members.stream().filter(item -> item.getUser().getUserId().equals(viewer)).findFirst().orElseThrow();
        return new ChannelResponse(channel.getId(), channel.getName(), channel.getType(),
                members.stream().map(item -> item.getUser().getUserId()).toList(), unread(member),
                members.stream().filter(item -> item.getRole() == ChannelRole.OWNER)
                        .map(item -> item.getUser().getUserId()).findFirst().orElse(null), lastMessageAt);
    }

    /** Sends a refresh hint to the supplied users after the surrounding transaction commits. */
    private void changed(Long id, Collection<String> recipients, String kind) {
        recipients.forEach(user -> events.user(user, "/queue/channel-events", Map.of("channelId", id, "kind", kind)));
    }

    /** Enforces a two-person direct chat or a named group with no more than 100 members. */
    private void validateChannel(CreateChannelRequest request, Set<String> ids) {
        if (ids.stream().anyMatch(id -> id == null || id.isBlank()) || ids.size() > 100
                || (request.type() == ChannelType.DM && ids.size() != 2)
                || (request.type() == ChannelType.GROUP && (ids.size() < 2 || request.name() == null || request.name().isBlank()))) {
            throw new BusinessException(ErrorCode.INVALID_CHANNEL);
        }
    }

    /** Produces a stable key for the same two user IDs regardless of their ordering. */
    private String directKey(Set<String> ids) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(String.join("\u0000", new TreeSet<>(ids)).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
