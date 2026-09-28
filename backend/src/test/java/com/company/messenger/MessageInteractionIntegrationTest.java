package com.company.messenger;

import com.company.messenger.domain.channel.*;
import com.company.messenger.domain.message.*;
import com.company.messenger.domain.user.*;
import com.company.messenger.global.auth.*;
import com.company.messenger.global.external.InternalAuthClient;
import com.company.messenger.global.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises persistent receipts, immutable history, reaction uniqueness, and read/delete races. */
@SpringBootTest(properties = {
        "spring.datasource.url=${MESSAGE_TEST_JDBC_URL:jdbc:h2:mem:interactions;MODE=MySQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.driver-class-name=${MESSAGE_TEST_JDBC_DRIVER:org.h2.Driver}",
        "spring.datasource.username=${MESSAGE_TEST_JDBC_USER:sa}",
        "spring.datasource.password=${MESSAGE_TEST_JDBC_PASSWORD:}"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MessageInteractionIntegrationTest {
    @Autowired ChannelService channels;
    @Autowired ChatService chat;
    @Autowired UserService users;
    @Autowired AuthService auth;
    @Autowired MessageReadReceiptRepository receipts;
    @Autowired MessageReactionRepository reactions;
    @Autowired com.company.messenger.domain.file.FileAttachmentRepository attachments;
    @Autowired UserRepository userRepository;
    @Autowired MockMvc mvc;
    @MockitoBean InternalAuthClient external;
    @MockitoBean PresenceService presence;
    @MockitoBean SessionRegistry sessions;
    @MockitoBean RefreshTokenStore refresh;

    /** Gives each test a fresh authenticated-session registry without any real company API or Redis. */
    @BeforeEach void setup() {
        var state = new ConcurrentHashMap<String, String>();
        var directory = List.of("alice", "bob", "carol", "outsider").stream()
                .map(id -> new InternalAuthClient.ExternalDirectoryUser(id, id, null, "development", "user")).toList();
        when(external.fetchUsers()).thenReturn(directory);
        when(external.login(anyString(), anyString())).thenAnswer(invocation -> directory.stream()
                .filter(user -> user.userId().equals(invocation.getArgument(0))).findFirst().orElseThrow());
        when(presence.getPresence(anyList())).thenReturn(List.of());
        when(sessions.findSessionId(anyString())).thenAnswer(invocation -> Optional.ofNullable(state.get(invocation.getArgument(0))));
        doAnswer(invocation -> { state.put(invocation.getArgument(0), invocation.getArgument(1)); return null; }).when(sessions).save(anyString(), anyString());
        users.resolveDirectoryUsers(List.of("alice", "bob", "carol", "outsider"));
    }

    @Test void readsUpdateEachMessageAndNeverMoveBackwards() {
        var channel = group("bob", "carol");
        var first = send(channel, "first");
        var second = send(channel, "second");
        assertThat(first.unreadCount()).isEqualTo(2);
        assertThat(first.deletable()).isTrue();
        channels.markRead("bob", channel, first.id());
        var firstRead = message(channel, first.id());
        assertThat(firstRead.readUserIds()).containsExactly("bob");
        assertThat(firstRead.unreadCount()).isEqualTo(1);
        assertThat(firstRead.deletable()).isFalse();
        assertThat(message(channel, second.id()).readUserIds()).isEmpty();
        channels.markRead("bob", channel, second.id());
        channels.markRead("bob", channel, first.id());
        channels.markRead("carol", channel, second.id());
        assertThat(message(channel, second.id()).readUserIds()).containsExactlyInAnyOrder("bob", "carol");
        assertThat(message(channel, second.id()).unreadCount()).isZero();
        assertThat(receipts.findForMessages(List.of(first.id(), second.id()))).hasSize(4);
    }

    @Test void directChatDisplaysUnreadThenReadAndDeniesDeletion() {
        var channel = channels.createChannel("alice", new CreateChannelRequest(null, ChannelType.DM, List.of("bob"))).id();
        var sent = send(channel, "direct message");
        assertThat(sent.unreadCount()).isEqualTo(1);
        channels.markRead("bob", channel, sent.id());
        var read = message(channel, sent.id());
        assertThat(read.unreadCount()).isZero();
        assertThat(read.readUserIds()).containsExactly("bob");
        assertAlreadyRead(() -> chat.delete("alice", sent.id()));
    }

    @Test void longHistoryIsMarkedInBoundedBatchesWithoutDroppingReceipts() {
        var channel = group("bob");
        var sent = new ArrayList<Long>();
        for (int n = 0; n < 251; n++) sent.add(send(channel, "batch " + n).id());
        assertThat(channels.markRead("bob", channel, sent.getLast())).isZero();
        assertThat(receipts.findForMessages(sent)).hasSize(251);
        assertThat(channels.markRead("bob", channel, sent.getLast())).isZero();
        assertThat(receipts.findForMessages(sent)).hasSize(251);
    }

    @Test void ownReadDoesNotBlockDeletionAndTombstoneRemains() {
        var channel = group("bob");
        var sent = send(channel, "private content");
        channels.markRead("alice", channel, sent.id());
        var deleted = chat.delete("alice", sent.id());
        assertThat(deleted.deleted()).isTrue();
        assertThat(deleted.content()).isEmpty();
        assertThat(deleted.deletable()).isFalse();
        assertThat(channels.getMessages("bob", channel, null, 30).items()).hasSize(1);
        channels.markRead("bob", channel, sent.id());
        assertThat(receipts.existsByMessageId(sent.id())).isFalse();
        assertThat(chat.delete("alice", sent.id()).deleted()).isTrue();
    }

    @Test void anyReaderBlocksGroupDeletionEvenAfterLeavingAndRejoining() {
        var channel = group("bob", "carol");
        var sent = send(channel, "keep this");
        channels.markRead("bob", channel, sent.id());
        channels.removeMember("bob", channel, "bob");
        assertAlreadyRead(() -> chat.delete("alice", sent.id()));
        channels.invite("alice", channel, new InviteMembersRequest(List.of("bob")));
        assertAlreadyRead(() -> chat.delete("alice", sent.id()));
        assertThat(message(channel, sent.id()).readUserIds()).containsExactly("bob");
    }

    @Test void selectionReplacesOneReactionAndTogglingRetainsReadHistory() {
        var channel = group("bob", "carol");
        var sent = send(channel, "thanks");
        chat.react("bob", sent.id(), "👍");
        chat.react("bob", sent.id(), "👍");
        var changed = chat.react("bob", sent.id(), "❤️");
        assertThat(changed.reactions()).containsExactly(new MessageReactionResponse("❤️", List.of("bob")));
        assertThat(changed.readUserIds()).containsExactly("bob");
        assertThat(channels.getChannels("bob").stream().filter(item -> item.id().equals(channel)).findFirst().orElseThrow().unreadCount()).isZero();
        var shared = chat.react("carol", sent.id(), "❤️");
        assertThat(shared.reactions().getFirst().userIds()).containsExactlyInAnyOrder("bob", "carol");
        var cleared = chat.removeReaction("bob", sent.id());
        assertThat(cleared.reactions()).containsExactly(new MessageReactionResponse("❤️", List.of("carol")));
        assertThat(cleared.readUserIds()).containsExactlyInAnyOrder("bob", "carol");
        assertAlreadyRead(() -> chat.delete("alice", sent.id()));
    }

    @Test void deletedMessagesRejectReactionsAndAuthorReactionsAreCleared() {
        var channel = group("bob");
        var sent = send(channel, "temporary");
        chat.react("alice", sent.id(), "👍");
        assertThat(chat.delete("alice", sent.id()).reactions()).isEmpty();
        assertThat(reactions.findForMessages(List.of(sent.id()))).isEmpty();
        assertThatThrownBy(() -> chat.react("bob", sent.id(), "👍")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> chat.removeReaction("bob", sent.id())).isInstanceOf(BusinessException.class);
    }

    @Test void outsidersInvalidEmojiAndOtherSendersAreRejected() {
        var channel = group("bob");
        var sent = send(channel, "protected");
        assertThatThrownBy(() -> chat.react("outsider", sent.id(), "👍")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> chat.removeReaction("outsider", sent.id())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> chat.react("bob", sent.id(), "arbitrary text")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> chat.delete("bob", sent.id())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> chat.edit("alice", sent.id(), new EditMessageRequest("changed"))).isInstanceOf(BusinessException.class);
    }

    @Test void concurrentSelectionsKeepOneRowForEachUser() throws Exception {
        var channel = group("bob");
        var sent = send(channel, "race");
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(6)) {
            var futures = new ArrayList<Future<?>>();
            for (String emoji : List.of("👍", "❤️", "😊", "😂", "😮", "🙏")) {
                futures.add(executor.submit(() -> { await(start); chat.react("bob", sent.id(), emoji); }));
            }
            start.countDown();
            for (var future : futures) future.get(15, TimeUnit.SECONDS);
        }
        assertThat(reactions.findForMessages(List.of(sent.id()))).hasSize(1);
        assertThat(receipts.findForMessages(List.of(sent.id()))).hasSize(1);
    }

    @Test void concurrentReadAndDeleteHaveOnlyTwoConsistentOutcomes() throws Exception {
        var channel = group("bob");
        for (int n = 0; n < 10; n++) {
            var sent = send(channel, "race " + n);
            var start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                var reader = executor.submit(() -> { await(start); channels.markRead("bob", channel, sent.id()); });
                var deletion = executor.submit(() -> {
                    await(start);
                    try { chat.delete("alice", sent.id()); return true; }
                    catch (BusinessException denied) {
                        assertThat(denied.getErrorCode()).isEqualTo(ErrorCode.MESSAGE_ALREADY_READ);
                        return false;
                    }
                });
                start.countDown();
                reader.get(15, TimeUnit.SECONDS);
                boolean deleted = deletion.get(15, TimeUnit.SECONDS);
                var actual = message(channel, sent.id());
                assertThat(actual.deleted()).isEqualTo(deleted);
                assertThat(actual.readUserIds().isEmpty()).isEqualTo(deleted);
            }
        }
    }

    @Test void httpRoutesUseAuthenticatedUserAndExposeConflictAfterRead() throws Exception {
        var channel = group("bob");
        var sent = send(channel, "http");
        var bob = auth.login(new LoginRequest("bob", "synthetic"), new MockHttpServletResponse()).accessToken();
        var alice = auth.login(new LoginRequest("alice", "synthetic"), new MockHttpServletResponse()).accessToken();
        mvc.perform(put("/api/v1/messages/{id}/reaction", sent.id()).header("Authorization", "Bearer " + bob)
                .contentType(MediaType.APPLICATION_JSON).content("{\"emoji\":\"👍\",\"userId\":\"alice\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.reactions[0].userIds[0]").value("bob"));
        mvc.perform(delete("/api/v1/messages/{id}/reaction", sent.id()).header("Authorization", "Bearer " + bob))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.reactions").isEmpty());
        mvc.perform(delete("/api/v1/messages/{id}", sent.id()).header("Authorization", "Bearer " + alice))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MESSAGE_005"));
        mvc.perform(put("/api/v1/messages/{id}/reaction", sent.id()).contentType(MediaType.APPLICATION_JSON).content("{\"emoji\":\"👍\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void repliesPersistAcrossPagesAndRecordOnlyTheOriginalAsRead() {
        var channel = group("bob", "carol");
        var original = send(channel, "회의는 오후 세 시입니다.");
        var unrelated = send(channel, "unread unrelated message");
        var request = new ChatMessageRequest(channel, "참석하겠습니다.", MessageType.TEXT, null,
                UUID.randomUUID().toString(), original.id());
        var reply = chat.saveMessage("bob", request);
        assertThat(reply.replyTo()).isEqualTo(new MessageReplyResponse(original.id(), "alice", original.content(), MessageType.TEXT, false));
        assertThat(chat.saveMessage("bob", request).id()).isEqualTo(reply.id());
        assertThat(channels.getMessages("bob", channel, null, 1).items().getFirst().replyTo()).isEqualTo(reply.replyTo());
        assertThat(message(channel, original.id()).readUserIds()).containsExactly("bob");
        assertThat(message(channel, unrelated.id()).readUserIds()).isEmpty();
        assertAlreadyRead(() -> chat.delete("alice", original.id()));
        var nested = chat.saveMessage("carol", new ChatMessageRequest(channel, "저도 참석합니다.", MessageType.TEXT, null, null, reply.id()));
        assertThat(nested.replyTo().id()).isEqualTo(reply.id());
        assertThat(nested.replyTo().content()).isEqualTo(reply.content());
        assertThat(channels.getMessages("alice", channel, null, 30).items()).hasSize(4);
    }

    @Test void repliesRejectMissingDeletedAndCrossChannelOriginals() {
        var channel = group("bob");
        var otherChannel = group("bob");
        var foreign = send(otherChannel, "private to another channel");
        var deleted = send(channel, "deleted original");
        chat.delete("alice", deleted.id());
        for (Long id : List.of(foreign.id(), deleted.id(), Long.MAX_VALUE)) {
            assertThatThrownBy(() -> chat.saveMessage("bob", new ChatMessageRequest(channel, "invalid reply", MessageType.TEXT, null, null, id)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REPLY));
        }
        assertThatThrownBy(() -> chat.saveMessage("outsider", new ChatMessageRequest(otherChannel, "no membership", MessageType.TEXT, null, null, foreign.id())))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CHANNEL_ACCESS_DENIED));
        assertThat(channels.getMessages("alice", channel, null, 30).items()).hasSize(1);
    }

    @Test void filesAndImagesCanBeQuotedAndSentAsReplies() {
        var channel = group("bob");
        var original = send(channel, "첨부 자료를 보내 주세요.");
        for (String mime : List.of("application/pdf", "image/png")) {
            var type = mime.startsWith("image/") ? MessageType.IMAGE : MessageType.FILE;
            var file = attachments.save(com.company.messenger.domain.file.FileAttachment.create(
                    type == MessageType.IMAGE ? "화면.png" : "자료.pdf", "synthetic-test-only", mime, 42,
                    userRepository.findByUserId("alice").orElseThrow()));
            var attachedReply = chat.saveMessage("alice", new ChatMessageRequest(channel, "caption", type, file.getId(), null, original.id()));
            assertThat(attachedReply.attachment().id()).isEqualTo(file.getId());
            assertThat(attachedReply.replyTo().id()).isEqualTo(original.id());
            var quote = chat.saveMessage("alice", new ChatMessageRequest(channel, "attachment reply", MessageType.TEXT, null, null, attachedReply.id()));
            assertThat(quote.replyTo().content()).isEqualTo(file.getOriginalName());
            assertThat(quote.replyTo().type()).isEqualTo(type);
            chat.delete("alice", attachedReply.id());
            assertThat(message(channel, quote.id()).replyTo().content()).isEmpty();
        }
    }

    @Test void deletingAnOriginalRemovesItsContentFromQuotesAndRetriesStayIdempotent() {
        var channel = group("bob");
        var original = send(channel, "sensitive original");
        var request = new ChatMessageRequest(channel, "self reply", MessageType.TEXT, null, UUID.randomUUID().toString(), original.id());
        var reply = chat.saveMessage("alice", request);
        chat.delete("alice", original.id());
        var stored = message(channel, reply.id());
        assertThat(stored.replyTo().id()).isEqualTo(original.id());
        assertThat(stored.replyTo().deleted()).isTrue();
        assertThat(stored.replyTo().content()).isEmpty();
        var retried = chat.saveMessage("alice", request);
        assertThat(retried.id()).isEqualTo(reply.id());
        assertThat(retried.replyTo().content()).isEmpty();
        assertThat(chat.delete("alice", reply.id()).replyTo()).isNull();
    }

    @Test void httpRepliesResolveTheOriginalOnTheServerAndValidateTheReference() throws Exception {
        var channel = group("bob");
        var original = send(channel, "trusted original");
        var token = auth.login(new LoginRequest("bob", "synthetic"), new MockHttpServletResponse()).accessToken();
        mvc.perform(post("/api/v1/channels/{id}/messages", channel).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"channelId":%d,"content":"reply","type":"TEXT","replyToMessageId":%d,
                         "replyTo":{"content":"forged original","senderUserId":"outsider"}}
                        """.formatted(channel, original.id())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.replyTo.id").value(original.id()))
                .andExpect(jsonPath("$.data.replyTo.senderUserId").value("alice"))
                .andExpect(jsonPath("$.data.replyTo.content").value("trusted original"));
        mvc.perform(get("/api/v1/channels/{id}/messages", channel).param("size", "1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].replyTo.id").value(original.id()));
        mvc.perform(post("/api/v1/channels/{id}/messages", channel).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"channelId":%d,"content":"reply","type":"TEXT","replyToMessageId":-1}
                        """.formatted(channel))).andExpect(status().isBadRequest());
    }

    @Test void concurrentReplyAndDeleteCannotQuoteAnAlreadyDeletedOriginal() throws Exception {
        var channel = group("bob");
        var original = send(channel, "reply deletion race");
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var reply = executor.submit(() -> {
                await(start);
                try { chat.saveMessage("bob", new ChatMessageRequest(channel, "reply", MessageType.TEXT, null, null, original.id())); return true; }
                catch (BusinessException denied) { assertThat(denied.getErrorCode()).isEqualTo(ErrorCode.INVALID_REPLY); return false; }
            });
            var deletion = executor.submit(() -> {
                await(start);
                try { chat.delete("alice", original.id()); return true; }
                catch (BusinessException denied) { assertThat(denied.getErrorCode()).isEqualTo(ErrorCode.MESSAGE_ALREADY_READ); return false; }
            });
            start.countDown();
            assertThat(reply.get(15, TimeUnit.SECONDS)).isNotEqualTo(deletion.get(15, TimeUnit.SECONDS));
        }
    }

    /** Makes each test independent by creating a fresh group rather than deleting shared identities. */
    private Long group(String... participants) {
        return channels.createChannel("alice", new CreateChannelRequest("interaction test", ChannelType.GROUP, List.of(participants))).id();
    }

    /** Sends a normal text message through the same service used by REST and WebSocket. */
    private MessageResponse send(Long channel, String text) {
        return chat.saveMessage("alice", new ChatMessageRequest(channel, text, MessageType.TEXT, null));
    }

    /** Reads one complete response from the real history endpoint service. */
    private MessageResponse message(Long channel, Long id) {
        return channels.getMessages("alice", channel, null, 100).items().stream().filter(message -> message.id().equals(id)).findFirst().orElseThrow();
    }

    /** Checks the exact policy failure instead of accepting an unrelated database exception. */
    private void assertAlreadyRead(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MESSAGE_ALREADY_READ));
    }

    /** Releases concurrent test operations together and preserves interruption failures. */
    private static void await(CountDownLatch start) {
        try { start.await(); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }
}
