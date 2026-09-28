package com.company.messenger.domain.message;

/** Resolves the current original, so deleted text and attachments never remain in a saved quote. */
public record MessageReplyResponse(Long id, String senderUserId, String content, MessageType type, boolean deleted) {
    public static MessageReplyResponse from(Message message) {
        return new MessageReplyResponse(
                message.getId(),
                message.getSender() == null ? null : message.getSender().getUserId(),
                message.isDeleted() ? "" : message.getFileAttachment() == null
                        ? message.getContent() : message.getFileAttachment().getOriginalName(),
                message.getType(),
                message.isDeleted());
    }
}
