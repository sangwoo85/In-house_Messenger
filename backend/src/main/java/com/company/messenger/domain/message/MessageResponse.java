package com.company.messenger.domain.message;

import java.time.LocalDateTime;
import java.util.List;

public record MessageResponse(
        Long id,
        Long channelId,
        String senderUserId,
        String content,
        MessageType type,
        MessageAttachmentResponse attachment,
        LocalDateTime createdAt,
        boolean deleted,
        LocalDateTime updatedAt,
        String clientRequestId,
        List<String> readUserIds,
        long unreadCount,
        List<MessageReactionResponse> reactions,
        boolean deletable,
        MessageReplyResponse replyTo
) {
    /** Builds a response with the separately loaded permanent read and reaction details. */
    public static MessageResponse from(Message message, List<String> readers, long unreadCount,
                                       List<MessageReactionResponse> reactions, MessageReplyResponse replyTo) {
        return new MessageResponse(
                message.getId(),
                message.getChannel().getId(),
                message.getSender() != null ? message.getSender().getUserId() : null,
                message.getContent(),
                message.getType(),
                message.getFileAttachment() != null
                        ? new MessageAttachmentResponse(
                                message.getFileAttachment().getId(),
                                message.getFileAttachment().getOriginalName(),
                                message.getFileAttachment().getMimeType(),
                                message.getFileAttachment().getFileSize(),
                                "/api/v1/files/" + message.getFileAttachment().getId(),
                                message.getFileAttachment().getMimeType().startsWith("image/")
                        )
                        : null,
                message.getCreatedAt(),
                message.isDeleted(),
                message.getUpdatedAt(),
                message.getClientRequestId(),
                readers,
                unreadCount,
                reactions,
                !message.isDeleted() && readers.isEmpty() && message.getSender() != null,
                message.isDeleted() ? null : replyTo
        );
    }
}
