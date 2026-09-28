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
    private final MessageDetailsService details;
    private final MessageReadReceiptRepository receipts;
    private final MessageReactionRepository reactions;
    private static final java.util.Set<String> ALLOWED_REACTIONS = java.util.Set.of("👍", "❤️", "😊", "😂", "😮", "🙏");
    private final UserRepository userRepository;

    /** Stores an idempotent message while serializing writes with channel membership changes. */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public MessageResponse saveMessage(String userId, ChatMessageRequest request) {
        Channel channel = channelRepository.findForUpdate(request.channelId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CHANNEL_NOT_FOUND));
        channelService.assertMembership(request.channelId(), userId);
        if (request.content() == null || request.content().isBlank() || request.content().length() > 4000) {
            throw new BusinessException(ErrorCode.INVALID_MESSAGE);
        }
        if (request.clientRequestId() != null) {
            var existing = messageRepository.findBySenderUserIdAndClientRequestId(userId, request.clientRequestId());
            if (existing.isPresent()) {
                var saved = existing.get();
                if (!saved.getChannel().getId().equals(request.channelId())) throw new BusinessException(ErrorCode.INVALID_MESSAGE);
                return details.response(saved);
            }
        }
        User sender = userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Message replyTarget = null;
        if (request.replyToMessageId() != null) {
            replyTarget = messageRepository.findById(request.replyToMessageId())
                    .filter(original -> original.getChannel().getId().equals(channel.getId()) && !original.isDeleted())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REPLY));
        }
        FileAttachment attachment = request.fileId() != null
                ? fileAttachmentRepository.findById(request.fileId())
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND))
                : null;
        validateMessage(userId, request, attachment);

        Message message = Message.create(
                channel,
                sender,
                request.content().trim(),
                request.type(),
                attachment
        );
        message.setClientRequestId(request.clientRequestId());
        message.setReplyToMessageId(request.replyToMessageId());
        messageRepository.save(message);
        // Replying proves this original was read, even when the client is browsing older history.
        if (replyTarget != null && replyTarget.getSender() != null
                && !replyTarget.getSender().getUserId().equals(userId)
                && !receipts.existsByMessageIdAndUserUserId(replyTarget.getId(), userId)) {
            receipts.save(new MessageReadReceipt(replyTarget, sender));
            replyTarget.touch();
            details.publish(replyTarget, "UPDATED");
        }
        return details.publish(message, "CREATED");
    }

    /** Validates membership before publishing a temporary typing indicator. */
    @Transactional(readOnly = true)
    public TypingEventResponse createTypingEvent(String userId, TypingIndicatorRequest request) {
        channelService.assertMembership(request.channelId(), userId);
        return new TypingEventResponse(request.channelId(), userId, request.typing());
    }

    /** Sent messages remain immutable, including requests from older desktop clients. */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public MessageResponse edit(String userId, Long messageId, EditMessageRequest request) {
        throw new BusinessException(ErrorCode.MESSAGE_EDIT_DENIED);
    }

    /** Deletes only the sender's unread message; a tombstone remains in channel history. */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public MessageResponse delete(String userId, Long messageId) {
        Message message = lockedMessage(userId, messageId);
        if (message.getSender() == null || !message.getSender().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.MESSAGE_DELETE_DENIED);
        }
        if (!message.isDeleted()) {
            if (receipts.existsByMessageId(messageId)) throw new BusinessException(ErrorCode.MESSAGE_ALREADY_READ);
            reactions.deleteByMessageId(messageId);
            message.delete();
        }
        return details.publish(message, "DELETED");
    }

    /** Replaces the user's single reaction atomically; reacting also proves the message was read. */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public MessageResponse react(String userId, Long messageId, String emoji) {
        Message message = lockedMessage(userId, messageId);
        if (message.isDeleted()) throw new BusinessException(ErrorCode.MESSAGE_REACTION_DENIED);
        if (emoji == null || !ALLOWED_REACTIONS.contains(emoji)) throw new BusinessException(ErrorCode.INVALID_REACTION);
        User user = userRepository.findByUserId(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        var selection = reactions.findByMessageIdAndUserUserId(messageId, userId);
        if (selection.isPresent()) selection.get().select(emoji);
        else reactions.save(new MessageReaction(message, user, emoji));
        if (message.getSender() != null && !message.getSender().getUserId().equals(userId)
                && !receipts.existsByMessageIdAndUserUserId(messageId, userId)) {
            receipts.save(new MessageReadReceipt(message, user));
        }
        message.touch();
        return details.publish(message, "UPDATED");
    }

    /** Removes only this user's reaction and deliberately retains the historical read receipt. */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public MessageResponse removeReaction(String userId, Long messageId) {
        Message message = lockedMessage(userId, messageId);
        if (message.isDeleted()) throw new BusinessException(ErrorCode.MESSAGE_REACTION_DENIED);
        reactions.findByMessageIdAndUserUserId(messageId, userId).ifPresent(reactions::delete);
        message.touch();
        return details.publish(message, "UPDATED");
    }

    /** Locks the channel first for every mutation, matching read operations and preventing deadlocks. */
    private Message lockedMessage(String userId, Long messageId) {
        var channelId = messageRepository.findChannelId(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));
        channelRepository.findForUpdate(channelId).orElseThrow(() -> new BusinessException(ErrorCode.CHANNEL_NOT_FOUND));
        channelService.assertMembership(channelId, userId);
        return messageRepository.findForUpdate(messageId).orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));
    }

    /** Allows text or an attachment owned by the sender, with a matching message type. */
    private void validateMessage(String userId, ChatMessageRequest request, FileAttachment attachment) {
        if (request.type() == MessageType.TEXT) {
            if (attachment != null) {
                throw new BusinessException(ErrorCode.INVALID_MESSAGE);
            }
            return;
        }

        if (request.type() != MessageType.IMAGE && request.type() != MessageType.FILE) {
            throw new BusinessException(ErrorCode.INVALID_MESSAGE);
        }
        if (attachment == null) {
            throw new BusinessException(ErrorCode.INVALID_MESSAGE);
        }
        if (!attachment.getUploader().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FILE_ACCESS_DENIED);
        }

        boolean image = attachment.getMimeType().startsWith("image/");
        if ((request.type() == MessageType.IMAGE) != image) {
            throw new BusinessException(ErrorCode.INVALID_MESSAGE);
        }
    }
}
