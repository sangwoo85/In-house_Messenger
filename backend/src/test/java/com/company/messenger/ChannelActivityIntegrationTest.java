package com.company.messenger;

import com.company.messenger.domain.channel.*;
import com.company.messenger.domain.file.FileAttachment;
import com.company.messenger.domain.file.FileAttachmentRepository;
import com.company.messenger.domain.message.*;
import com.company.messenger.domain.user.*;
import com.company.messenger.global.auth.RefreshTokenStore;
import com.company.messenger.global.auth.SessionRegistry;
import com.company.messenger.global.external.InternalAuthClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/** Verifies channel activity means the original send time rather than a later read, reaction, or deletion. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:channel_activity;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class ChannelActivityIntegrationTest {
    @Autowired ChannelService channels;
    @Autowired ChatService chat;
    @Autowired UserService users;
    @Autowired UserRepository userRepository;
    @Autowired FileAttachmentRepository files;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean MessageRepository messages;
    @MockitoBean InternalAuthClient external;
    @MockitoBean PresenceService presence;
    @MockitoBean SessionRegistry sessions;
    @MockitoBean RefreshTokenStore refresh;

    /** Uses synthetic directory users with an isolated in-memory database and no production services. */
    @BeforeEach void setUp() {
        when(external.fetchUsers()).thenReturn(List.of("activity-alice", "activity-bob", "activity-carol").stream()
                .map(id -> new InternalAuthClient.ExternalDirectoryUser(id, id, null, "development", "user")).toList());
        users.resolveDirectoryUsers(List.of("activity-alice", "activity-bob", "activity-carol"));
    }

    @Test void newChannelHasNoSendTimeUntilFirstTextMessage() {
        var channel = group();
        assertThat(channel.lastMessageAt()).isNull();
        assertThat(listed(channel.id()).lastMessageAt()).isNull();
        var first = send(channel.id(), "first");
        assertThat(listed(channel.id()).lastMessageAt()).isEqualTo(first.createdAt());
    }

    @Test void newestFileSendTimeSurvivesReadReactionAndDeletion() {
        var channel = group();
        var first = send(channel.id(), "first");
        var attachment = files.save(FileAttachment.create("activity.txt", "synthetic-not-downloaded", "text/plain", 4,
                userRepository.findByUserId("activity-alice").orElseThrow()));
        var latest = chat.saveMessage("activity-alice", new ChatMessageRequest(channel.id(), attachment.getOriginalName(), MessageType.FILE, attachment.getId()));
        assertThat(listed(channel.id()).lastMessageAt()).isEqualTo(latest.createdAt());

        channels.markRead("activity-bob", channel.id(), first.id());
        chat.react("activity-bob", first.id(), "👍");
        chat.react("activity-alice", latest.id(), "❤️");
        chat.removeReaction("activity-alice", latest.id());
        assertThat(listed(channel.id()).lastMessageAt()).isEqualTo(latest.createdAt());
        assertThat(chat.delete("activity-alice", latest.id()).deleted()).isTrue();
        assertThat(listed(channel.id()).lastMessageAt()).isEqualTo(latest.createdAt());
    }

    @Test void listingUsesOneAggregateAndMaximumCreationTimeInsteadOfMessageIdOrUpdateTime() {
        var channel = group();
        var empty = group();
        var newerSend = send(channel.id(), "earlier ID but newer send date");
        var olderSend = send(channel.id(), "later ID but older send date");
        var expected = LocalDateTime.of(2025, 8, 12, 9, 30);
        jdbc.update("update messages set created_at = ?, updated_at = ? where id = ?", expected, expected, newerSend.id());
        jdbc.update("update messages set created_at = ?, updated_at = ? where id = ?", expected.minusDays(1), expected.plusDays(1), olderSend.id());
        clearInvocations(messages);

        var listed = channels.getChannels("activity-alice");
        assertThat(listed.stream().filter(item -> item.id().equals(channel.id())).findFirst().orElseThrow().lastMessageAt()).isEqualTo(expected);
        assertThat(listed.stream().filter(item -> item.id().equals(empty.id())).findFirst().orElseThrow().lastMessageAt()).isNull();
        verify(messages, times(1)).findLastMessageTimes(anyList());
    }

    @Test void reusedDirectConversationAndInvitationReturnExistingSendTime() {
        var request = new CreateChannelRequest(null, ChannelType.DM, List.of("activity-bob"));
        var direct = channels.createChannel("activity-alice", request);
        var directMessage = send(direct.id(), "direct history");
        var reused = channels.createChannel("activity-alice", request);
        assertThat(reused.id()).isEqualTo(direct.id());
        assertThat(reused.lastMessageAt()).isEqualTo(directMessage.createdAt());

        var group = group();
        var groupMessage = send(group.id(), "before invitation");
        var invited = channels.invite("activity-alice", group.id(), new InviteMembersRequest(List.of("activity-carol")));
        assertThat(invited.lastMessageAt()).isEqualTo(groupMessage.createdAt());
    }

    /** Creates independent groups so tests do not delete or depend on another test's conversation. */
    private ChannelResponse group() {
        return channels.createChannel("activity-alice", new CreateChannelRequest("activity test", ChannelType.GROUP, List.of("activity-bob")));
    }

    /** Sends through the same service used by authenticated REST and WebSocket requests. */
    private MessageResponse send(Long channelId, String text) {
        return chat.saveMessage("activity-alice", new ChatMessageRequest(channelId, text, MessageType.TEXT, null));
    }

    /** Retrieves the activity returned to the actual conversation list. */
    private ChannelResponse listed(Long channelId) {
        return channels.getChannels("activity-alice").stream().filter(channel -> channel.id().equals(channelId)).findFirst().orElseThrow();
    }
}
