package com.company.messenger.domain.message;

import com.company.messenger.domain.channel.Channel;
import com.company.messenger.domain.channel.ChannelRepository;
import com.company.messenger.domain.channel.ChannelService;
import com.company.messenger.domain.file.FileAttachment;
import com.company.messenger.domain.file.FileAttachmentRepository;
import com.company.messenger.domain.user.User;
import com.company.messenger.domain.user.UserRepository;
import com.company.messenger.global.exception.BusinessException;
import com.company.messenger.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChannelService channelService;
    private final ChannelRepository channelRepository;
    private final MessageRepository messageRepository;
    private final FileAttachmentRepository fileAttachmentRepository;
    private final UnreadCountService unreadCountService;
    private final UserRepository userRepository;

    @Transactional
    public MessageResponse saveMessage(String userId, ChatMessageRequest request) {
        channelService.assertMembership(request.channelId(), userId);

        Channel channel = channelRepository.findById(request.channelId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CHANNEL_NOT_FOUND));
        User sender = userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        FileAttachment attachment = request.fileId() != null
                ? fileAttachmentRepository.findById(request.fileId())
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND))
                : null;

        Message message = messageRepository.save(Message.create(channel, sender, request.content(), request.type(), attachment));
        channelService.getActiveMemberUserIds(channel.getId()).stream()
                .filter(memberUserId -> !memberUserId.equals(userId))
                .forEach(memberUserId -> unreadCountService.increment(channel.getId(), memberUserId));
        return MessageResponse.from(message);
    }

    @Transactional(readOnly = true)
    public TypingEventResponse createTypingEvent(String userId, TypingIndicatorRequest request) {
        channelService.assertMembership(request.channelId(), userId);
        return new TypingEventResponse(request.channelId(), userId, request.typing());
    }
}
