package com.company.messenger.domain.message;

import com.company.messenger.domain.channel.ChannelMember;
import com.company.messenger.domain.channel.ChannelMemberRepository;
import com.company.messenger.global.response.RealtimeEvents;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/** Loads message details in batches and sends the same state to every channel member. */
@Service
@RequiredArgsConstructor
public class MessageDetailsService {
    private final ChannelMemberRepository members;
    private final MessageReadReceiptRepository receipts;
    private final MessageReactionRepository reactions;
    private final RealtimeEvents events;
    private final MessageRepository messageRepository;

    /** Builds one complete message response inside the caller's transaction. */
    public MessageResponse response(Message message) { return responses(List.of(message)).getFirst(); }

    /** Uses one query for each detail table instead of querying once per message in a page. */
    public List<MessageResponse> responses(List<Message> messages) {
        if (messages.isEmpty()) return List.of();
        var ids = messages.stream().map(Message::getId).toList();
        var readers = receipts.findForMessages(ids).stream().collect(Collectors.groupingBy(
                receipt -> receipt.getMessage().getId(), LinkedHashMap::new,
                Collectors.mapping(receipt -> receipt.getUser().getUserId(), Collectors.toList())));
        var selections = reactions.findForMessages(ids).stream().collect(Collectors.groupingBy(
                reaction -> reaction.getMessage().getId(), LinkedHashMap::new, Collectors.toList()));
        var activeMembers = members.findActiveMembers(messages.getFirst().getChannel().getId());
        var replyIds = messages.stream().filter(message -> !message.isDeleted())
                .map(Message::getReplyToMessageId).filter(Objects::nonNull).distinct().toList();
        Map<Long, MessageReplyResponse> replies = replyIds.isEmpty() ? Map.of()
                : messageRepository.findReplyTargets(messages.getFirst().getChannel().getId(), replyIds).stream()
                .collect(Collectors.toMap(Message::getId, MessageReplyResponse::from));
        return messages.stream().map(message -> {
            var readUsers = readers.getOrDefault(message.getId(), List.of());
            var byEmoji = selections.getOrDefault(message.getId(), List.of()).stream().collect(Collectors.groupingBy(
                    MessageReaction::getEmoji, LinkedHashMap::new,
                    Collectors.mapping(reaction -> reaction.getUser().getUserId(), Collectors.toList())));
            var grouped = byEmoji.entrySet().stream().map(entry -> new MessageReactionResponse(entry.getKey(), entry.getValue())).toList();
            long unread = message.isDeleted() ? 0 : activeMembers.stream()
                    .filter(member -> wasRecipient(member, message))
                    .filter(member -> !readUsers.contains(member.getUser().getUserId())).count();
            return MessageResponse.from(message, readUsers, unread, grouped,
                    message.getReplyToMessageId() == null ? null : replies.get(message.getReplyToMessageId()));
        }).toList();
    }

    /** Enqueues complete responses for delivery only after the database transaction commits. */
    public MessageResponse publish(Message message, String kind) {
        var response = response(message);
        deliver(response, kind, members.findActiveMembers(message.getChannel().getId()));
        return response;
    }

    /** Batches read updates before broadcasting them to the current channel members. */
    public void publishReadUpdates(List<Message> messages) {
        if (messages.isEmpty()) return;
        var recipients = members.findActiveMembers(messages.getFirst().getChannel().getId());
        responses(messages).forEach(response -> deliver(response, "UPDATED", recipients));
    }

    /** An unread indicator counts current members who were present when the message was sent. */
    private boolean wasRecipient(ChannelMember member, Message message) {
        return message.getSender() != null
                && !member.getUser().getId().equals(message.getSender().getId())
                && !member.getJoinedAt().isAfter(message.getCreatedAt());
    }

    /** Keeps the legacy channel topic and the reconnect-safe per-user message queue in sync. */
    private void deliver(MessageResponse response, String kind, List<ChannelMember> recipients) {
        events.topic("/topic/channel/" + response.channelId(), response);
        for (var member : recipients) {
            events.user(member.getUser().getUserId(), "/queue/messages", Map.of("kind", kind, "message", response));
        }
    }
}
