package com.company.messenger.domain.channel;

import com.company.messenger.domain.message.Message;
import com.company.messenger.domain.message.MessageRepository;
import com.company.messenger.domain.message.MessageResponse;
import com.company.messenger.domain.message.MessageSliceResponse;
import com.company.messenger.domain.message.UnreadCountService;
import com.company.messenger.domain.user.User;
import com.company.messenger.domain.user.UserRepository;
import com.company.messenger.global.exception.BusinessException;
import com.company.messenger.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChannelService {

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final MessageRepository messageRepository;
    private final UnreadCountService unreadCountService;
    private final UserRepository userRepository;

    @Transactional
    public ChannelResponse createChannel(String ownerUserId, CreateChannelRequest request) {
        User owner = getUser(ownerUserId);

        LinkedHashSet<String> memberUserIds = new LinkedHashSet<>(request.memberUserIds());
        memberUserIds.add(ownerUserId);

        if (request.type() == ChannelType.DM && memberUserIds.size() == 2) {
            String targetUserId = memberUserIds.stream()
                    .filter(userId -> !userId.equals(ownerUserId))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

            ChannelResponse existingDirectMessage = findExistingDirectMessage(ownerUserId, targetUserId);
            if (existingDirectMessage != null) {
                return existingDirectMessage;
            }
        }

        List<User> members = memberUserIds.stream()
                .map(this::getUser)
                .toList();

        Channel channel = channelRepository.save(Channel.create(request.name(), request.type(), owner));

        List<ChannelMember> memberships = new ArrayList<>();
        for (User member : members) {
            ChannelRole role = member.getUserId().equals(ownerUserId) ? ChannelRole.OWNER : ChannelRole.MEMBER;
            memberships.add(ChannelMember.join(channel, member, role));
        }
        channelMemberRepository.saveAll(memberships);

        return toResponse(channel, memberships, ownerUserId);
    }

    @Transactional(readOnly = true)
    public List<ChannelResponse> getChannels(String userId) {
        List<ChannelMember> memberships = channelMemberRepository.findActiveByUserId(userId);
        return memberships.stream()
                .map(membership -> {
                    List<String> members = channelMemberRepository.findActiveMembers(membership.getChannel().getId()).stream()
                            .map(channelMember -> channelMember.getUser().getUserId())
                            .toList();
                    return new ChannelResponse(
                            membership.getChannel().getId(),
                            membership.getChannel().getName(),
                            membership.getChannel().getType(),
                            members,
                            unreadCountService.get(membership.getChannel().getId(), userId)
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public MessageSliceResponse getMessages(String userId, Long channelId, Long cursor, int size) {
        assertMembership(channelId, userId);

        List<Message> messages = messageRepository.findMessages(channelId, cursor, PageRequest.of(0, size + 1));
        boolean hasNext = messages.size() > size;
        List<Message> page = hasNext ? messages.subList(0, size) : messages;
        List<MessageResponse> items = page.stream()
                .map(MessageResponse::from)
                .sorted((left, right) -> left.id().compareTo(right.id()))
                .toList();
        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;

        return new MessageSliceResponse(items, nextCursor, hasNext);
    }

    @Transactional(readOnly = true)
    public void assertMembership(Long channelId, String userId) {
        channelMemberRepository.findActiveMembership(channelId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHANNEL_ACCESS_DENIED));
    }

    @Transactional
    public void markRead(String userId, Long channelId, Long messageId) {
        ChannelMember membership = channelMemberRepository.findActiveMembership(channelId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHANNEL_ACCESS_DENIED));
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));
        membership.markRead(message);
        unreadCountService.reset(channelId, userId);
    }

    @Transactional(readOnly = true)
    public List<String> getActiveMemberUserIds(Long channelId) {
        return channelMemberRepository.findActiveMembers(channelId).stream()
                .map(channelMember -> channelMember.getUser().getUserId())
                .toList();
    }

    private ChannelResponse findExistingDirectMessage(String ownerUserId, String targetUserId) {
        Set<String> expectedMembers = Set.of(ownerUserId, targetUserId);

        return channelMemberRepository.findActiveByUserId(ownerUserId).stream()
                .map(ChannelMember::getChannel)
                .filter(channel -> channel.getType() == ChannelType.DM)
                .map(channel -> {
                    List<ChannelMember> activeMembers = channelMemberRepository.findActiveMembers(channel.getId());
                    return new Object[]{channel, activeMembers};
                })
                .filter(tuple -> {
                    @SuppressWarnings("unchecked")
                    List<ChannelMember> activeMembers = (List<ChannelMember>) tuple[1];
                    return activeMembers.size() == 2
                            && activeMembers.stream()
                            .map(channelMember -> channelMember.getUser().getUserId())
                            .collect(java.util.stream.Collectors.toSet())
                            .equals(expectedMembers);
                })
                .map(tuple -> toResponse((Channel) tuple[0], castMembers(tuple[1]), ownerUserId))
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private List<ChannelMember> castMembers(Object value) {
        return (List<ChannelMember>) value;
    }

    private ChannelResponse toResponse(Channel channel, List<ChannelMember> memberships, String viewerUserId) {
        return new ChannelResponse(
                channel.getId(),
                channel.getName(),
                channel.getType(),
                memberships.stream().map(member -> member.getUser().getUserId()).toList(),
                unreadCountService.get(channel.getId(), viewerUserId)
        );
    }

    private User getUser(String userId) {
        return userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
