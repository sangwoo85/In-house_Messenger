package com.company.messenger;

import com.company.messenger.domain.channel.*;
import com.company.messenger.domain.message.*;
import com.company.messenger.domain.file.*;
import com.company.messenger.domain.notice.*;
import com.company.messenger.domain.user.*;
import com.company.messenger.global.auth.*;
import com.company.messenger.global.external.InternalAuthClient;
import com.company.messenger.global.exception.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:workflow;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "app.file.storage-path=target/workflow-uploads"
})
@ActiveProfiles("test")
class MessengerWorkflowIntegrationTest {
    @LocalServerPort int port;
    @Autowired AuthService auth;
    @Autowired UserService userService;
    @Autowired UserRepository users;
    @Autowired ChannelService channels;
    @Autowired ChatService chat;
    @Autowired FileService files;
    @Autowired NoticeService notices;
    @Autowired MessageRepository messages;
    @Autowired SimpMessagingTemplate messaging;
    @Autowired com.company.messenger.global.response.RealtimeEvents events;
    @MockitoBean SessionRegistry sessions;
    @MockitoBean RefreshTokenStore refresh;
    @MockitoBean InternalAuthClient external;
    @MockitoBean PresenceService presence;
    Map<String, String> sessionState;
    List<InternalAuthClient.ExternalDirectoryUser> directory;

    @BeforeEach void setup() {
        sessionState = new ConcurrentHashMap<>();
        directory = List.of("alice", "bob", "carol", "outsider").stream()
                .map(id -> new InternalAuthClient.ExternalDirectoryUser(id, id, null, "development", "user")).toList();
        when(external.login(anyString(), anyString())).thenAnswer(call ->
                new InternalAuthClient.ExternalDirectoryUser(call.getArgument(0), call.getArgument(0), null, "development", "user"));
        when(external.fetchUsers()).thenReturn(directory);
        when(sessions.findSessionId(anyString())).thenAnswer(i -> Optional.ofNullable(sessionState.get(i.getArgument(0))));
        doAnswer(i -> { sessionState.put(i.getArgument(0), i.getArgument(1)); return null; }).when(sessions).save(anyString(), anyString());
        doAnswer(i -> { sessionState.remove(i.getArgument(0)); return null; }).when(sessions).delete(anyString());
        when(presence.getPresence(anyList())).thenReturn(List.of());
        userService.resolveDirectoryUsers(List.of("alice", "bob", "carol", "outsider"));
    }

    String login(String id) { return auth.login(new LoginRequest(id, "synthetic-only"), new MockHttpServletResponse()).accessToken(); }
    ChannelResponse group() { return channels.createChannel("alice", new CreateChannelRequest("group", ChannelType.GROUP, List.of("bob"))); }
    MessageResponse send(Long channel, String text) { return chat.saveMessage("alice", new ChatMessageRequest(channel, text, MessageType.TEXT, null)); }
    long unread(Long channel) { return channels.getChannels("bob").stream().filter(item -> item.id().equals(channel)).findFirst().orElseThrow().unreadCount(); }

    @Test void twoMegabyteMultipartReachesService() {
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new ByteArrayResource(new byte[2 * 1024 * 1024]) {
            @Override public String getFilename() { return "valid-2mb.pdf"; }
        });
        var headers = new HttpHeaders(); headers.setBearerAuth(login("alice")); headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        var response = new RestTemplate().postForEntity("http://localhost:" + port + "/api/v1/files/upload", new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test void attachmentAccessTracksSharingAndMembership() {
        var channel = group();
        var file = files.upload("alice", new MockMultipartFile("file", "private.txt", "text/plain", "synthetic".getBytes()));
        assertThatThrownBy(() -> files.download("bob", file.id())).isInstanceOf(BusinessException.class);
        var message = chat.saveMessage("alice", new ChatMessageRequest(channel.id(), "file", MessageType.FILE, file.id()));
        assertThat(files.download("bob", file.id()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThatThrownBy(() -> files.download("outsider", file.id())).isInstanceOf(BusinessException.class);
        channels.removeMember("alice", channel.id(), "bob");
        assertThatThrownBy(() -> files.download("bob", file.id())).isInstanceOf(BusinessException.class);
        channels.invite("alice", channel.id(), new InviteMembersRequest(List.of("bob")));
        chat.delete("alice", message.id());
        assertThatThrownBy(() -> files.download("bob", file.id())).isInstanceOf(BusinessException.class);
    }

    @Test void oldReadRequestPreservesNewMessagesAndCannotMoveCursorBackwards() {
        var channel = group();
        var first = send(channel.id(), "first"); var second = send(channel.id(), "second");
        assertThat(unread(channel.id())).isEqualTo(2);
        assertThat(channels.markRead("bob", channel.id(), first.id())).isEqualTo(1);
        assertThat(unread(channel.id())).isEqualTo(1);
        channels.markRead("bob", channel.id(), second.id());
        channels.markRead("bob", channel.id(), first.id());
        assertThat(unread(channel.id())).isZero();
    }

    @Test void ownerControlsMembershipAndTransfersOwnershipOnLeave() {
        var channel = group();
        assertThatThrownBy(() -> channels.invite("bob", channel.id(), new InviteMembersRequest(List.of("carol")))).isInstanceOf(BusinessException.class);
        var invited = channels.invite("alice", channel.id(), new InviteMembersRequest(List.of("carol")));
        assertThat(invited.members()).containsExactlyInAnyOrder("alice", "bob", "carol");
        assertThatThrownBy(() -> channels.removeMember("bob", channel.id(), "alice")).isInstanceOf(BusinessException.class);
        channels.removeMember("alice", channel.id(), "alice");
        var remaining = channels.getChannels("bob").stream().filter(item -> item.id().equals(channel.id())).findFirst().orElseThrow();
        assertThat(remaining.ownerUserId()).isEqualTo("bob");
        assertThatThrownBy(() -> channels.getMessages("alice", channel.id(), null, 30)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> channels.invite("bob", channel.id(), new InviteMembersRequest(List.of("unknown")))).isInstanceOf(BusinessException.class);
    }

    @Test void messagesCannotBeEditedButAuthorCanDelete() {
        var channel = group(); var message = send(channel.id(), "before");
        assertThatThrownBy(() -> chat.edit("bob", message.id(), new EditMessageRequest("forged"))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> chat.delete("bob", message.id())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> chat.edit("alice", message.id(), new EditMessageRequest("after"))).isInstanceOf(BusinessException.class);
        var headers = new HttpHeaders(); headers.setBearerAuth(login("alice"));
        assertThatThrownBy(() -> new RestTemplate(new org.springframework.http.client.JdkClientHttpRequestFactory()).exchange(
                "http://localhost:" + port + "/api/v1/messages/" + message.id(), HttpMethod.PATCH,
                new HttpEntity<>(new EditMessageRequest("after"), headers), String.class))
                .isInstanceOf(org.springframework.web.client.HttpClientErrorException.Forbidden.class);
        assertThat(messages.findById(message.id()).orElseThrow().getContent()).isEqualTo("before");
        var deleted = chat.delete("alice", message.id());
        assertThat(deleted.deleted()).isTrue(); assertThat(deleted.content()).isEmpty();
        assertThat(channels.getMessages("bob", channel.id(), null, 30).items().getFirst().deleted()).isTrue();
    }

    @Test void retryWithSameRequestIdDoesNotDuplicateStoredMessage() {
        var channel = group(); var request = new ChatMessageRequest(channel.id(), "retry", MessageType.TEXT, null, UUID.randomUUID().toString());
        var first = chat.saveMessage("alice", request); var retry = chat.saveMessage("alice", request);
        assertThat(retry.id()).isEqualTo(first.id());
        assertThat(channels.getMessages("alice", channel.id(), null, 30).items()).hasSize(1);
    }

    @Test void longMessageIsRejectedByActualHttpEndpoint() {
        var channel = group(); var headers = new HttpHeaders(); headers.setBearerAuth(login("alice")); headers.setContentType(MediaType.APPLICATION_JSON);
        var request = new ChatMessageRequest(channel.id(), "x".repeat(4001), MessageType.TEXT, null, UUID.randomUUID().toString());
        assertThatThrownBy(() -> new RestTemplate().postForEntity("http://localhost:" + port + "/api/v1/channels/" + channel.id() + "/messages", new HttpEntity<>(request, headers), String.class))
                .isInstanceOf(org.springframework.web.client.HttpClientErrorException.BadRequest.class);
        assertThat(channels.getMessages("alice", channel.id(), null, 30).items()).isEmpty();
    }

    @Test void externalDirectoryIsAuthoritativeEvenWithExistingLocalRows() {
        when(external.fetchUsers()).thenReturn(List.of());
        when(external.login(eq("alice"), anyString())).thenThrow(new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        assertThatThrownBy(() -> login("alice")).isInstanceOf(BusinessException.class);
        assertThat(userService.getDirectory("alice")).isEmpty();
        when(external.fetchUsers()).thenThrow(new BusinessException(ErrorCode.EXTERNAL_AUTH_UNAVAILABLE));
        assertThatThrownBy(() -> userService.getDirectory("alice")).isInstanceOf(BusinessException.class);
    }

    @Test void notificationCountIncludesUnreadBeyondFirstPageAndNoticesHaveHistory() {
        for (int n = 0; n < 25; n++) notices.notifyUser(new NotifyUserRequest("carol", "notice " + n, "body", null));
        var first = notices.getNotifications("carol", 0, 20);
        assertThat(first.items()).hasSize(20); assertThat(first.unreadCount()).isGreaterThanOrEqualTo(25);
        first.items().forEach(item -> notices.markNotificationRead("carol", item.id()));
        assertThat(notices.getNotifications("carol", 0, 20).unreadCount()).isGreaterThanOrEqualTo(5);
        assertThat(notices.getNotifications("carol", 1, 20).items()).hasSizeGreaterThanOrEqualTo(5);
        var notice = notices.broadcast(new BroadcastNoticeRequest("company notice", "saved", "system"));
        assertThat(notices.getNotices(0, 20).items()).anyMatch(item -> item.id().equals(notice.id()));
    }

    @Test void revokedSocketCannotReceiveAnyLaterTraffic() throws Exception {
        String token = login("alice"); var channel = group(); String destination = "/topic/channel/" + channel.id();
        try (var socket = connect(token, destination)) {
            socket.ready(() -> messaging.convertAndSend(destination, "ready"));
            auth.logout("alice", new MockHttpServletResponse());
            messaging.convertAndSend(destination, "private-after-logout");
            assertThat(socket.received.poll(500, TimeUnit.MILLISECONDS)).isNull();
        }
    }

    @Test void userQueueReceivesFirstMessageWithoutKnowingChannelId() throws Exception {
        try (var socket = connect(login("bob"), "/user/queue/messages")) {
            socket.ready(() -> messaging.convertAndSendToUser("bob", "/queue/messages", "ready"));
            var channel = group();
            var first = send(channel.id(), "first-message-in-new-channel");
            assertThat(socket.received.poll(2, TimeUnit.SECONDS)).contains("first-message-in-new-channel");
            var reply = chat.saveMessage("alice", new ChatMessageRequest(channel.id(), "reply-over-websocket",
                    MessageType.TEXT, null, UUID.randomUUID().toString(), first.id()));
            var payload = new com.fasterxml.jackson.databind.ObjectMapper().readTree(socket.received.poll(2, TimeUnit.SECONDS));
            assertThat(payload.path("message").path("id").asLong()).isEqualTo(reply.id());
            assertThat(payload.path("message").path("replyTo").path("id").asLong()).isEqualTo(first.id());
            assertThat(payload.path("message").path("replyTo").path("content").asText()).isEqualTo(first.content());
        }
    }

    @Test void removedMemberCannotReceiveQueuedChannelMessage() throws Exception {
        var channel = group();
        var message = send(channel.id(), "queued-private-message");
        try (var socket = connect(login("bob"), "/user/queue/messages")) {
            socket.ready(() -> messaging.convertAndSendToUser("bob", "/queue/messages", "ready"));
            channels.removeMember("alice", channel.id(), "bob");
            events.user("bob", "/queue/messages", Map.of("kind", "CREATED", "message", message));
            assertThat(socket.received.poll(500, TimeUnit.MILLISECONDS)).isNull();
        }
    }

    private TestSocket connect(String token, String destination) throws Exception {
        var client = new WebSocketStompClient(new StandardWebSocketClient());
        var converter = new StringMessageConverter() {
            @Override protected boolean supportsMimeType(org.springframework.messaging.MessageHeaders headers) { return true; }
        };
        client.setMessageConverter(converter);
        var headers = new StompHeaders(); headers.add("Authorization", "Bearer " + token);
        var connection = client.connectAsync("ws://localhost:" + port + "/ws", new org.springframework.web.socket.WebSocketHttpHeaders(), headers, new StompSessionHandlerAdapter() {}).get(10, TimeUnit.SECONDS);
        var socket = new TestSocket(client, connection);
        connection.subscribe(destination, new StompFrameHandler() {
            public Type getPayloadType(StompHeaders headers) { return String.class; }
            public void handleFrame(StompHeaders headers, Object payload) { socket.received.add((String) payload); }
        });
        return socket;
    }

    private static class TestSocket implements AutoCloseable {
        final WebSocketStompClient client; final StompSession session; final BlockingQueue<String> received = new LinkedBlockingQueue<>();
        TestSocket(WebSocketStompClient client, StompSession session) { this.client = client; this.session = session; }
        void ready(Runnable publish) throws Exception {
            for (int n = 0; n < 30; n++) { publish.run(); if (received.poll(100, TimeUnit.MILLISECONDS) != null) { received.clear(); return; } }
            fail("Subscription was not established");
        }
        public void close() { if (session.isConnected()) session.disconnect(); client.stop(); }
    }
}
