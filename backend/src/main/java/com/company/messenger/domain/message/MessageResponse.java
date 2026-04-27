package com.company.messenger.domain.message;

import java.time.LocalDateTime;

public record MessageResponse(
        Long id,
        Long channelId,
        String senderUserId,
        String content,
        MessageType type,
        MessageAttachmentResponse attachment,
        LocalDateTime createdAt,
        boolean deleted
) {
    public static MessageResponse from(Message message) {
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
                message.isDeleted()
        );
    }
}
