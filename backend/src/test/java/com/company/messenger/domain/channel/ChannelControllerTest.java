package com.company.messenger.domain.channel;

import com.company.messenger.domain.message.ChatMessageRequest;
import com.company.messenger.domain.message.ChatService;
import com.company.messenger.domain.message.MessageSliceResponse;
import com.company.messenger.domain.message.MessageType;
import com.company.messenger.domain.message.MessageRepository;
import com.company.messenger.domain.user.JsonTestUtils;
import com.company.messenger.domain.message.UnreadCountService;
import com.company.messenger.domain.user.PresenceService;
import com.company.messenger.domain.user.User;
import com.company.messenger.domain.user.UserRepository;
import com.company.messenger.global.auth.RefreshTokenStore;
import com.company.messenger.global.auth.SessionExpiryNotifier;
import com.company.messenger.global.auth.SessionRegistry;
import com.company.messenger.global.external.InternalAuthClient;
import com.company.messenger.global.exception.BusinessException;
import com.company.messenger.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChannelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChannelService channelService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private ChannelMemberRepository channelMemberRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private InternalAuthClient internalAuthClient;

    @MockitoBean
    private PresenceService presenceService;

    @MockitoBean
    private UnreadCountService unreadCountService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.when(internalAuthClient.fetchUsers()).thenReturn(java.util.List.of(
                new InternalAuthClient.ExternalDirectoryUser("user01", "user01", null, "개발팀", "사용자"),
                new InternalAuthClient.ExternalDirectoryUser("user02", "user02", null, "개발팀", "사용자"),
                new InternalAuthClient.ExternalDirectoryUser("user03", "user03", null, "개발팀", "사용자")));

        when(internalAuthClient.login(anyString(), anyString())).thenAnswer(call ->
                new InternalAuthClient.ExternalDirectoryUser(call.getArgument(0), call.getArgument(0), null, "개발팀", "사용자"));
        messageRepository.deleteAll();
        channelMemberRepository.deleteAll();
        channelRepository.deleteAll();
        userRepository.deleteAll();
        ensureUser("user01");
        ensureUser("user02");
        ensureUser("user03");
    }

    @Test
    void createChannelAndListChannelsShouldReturnMemberships() throws Exception {
        String accessToken = loginAndGetAccessToken("user01");

        mockMvc.perform(post("/api/v1/channels")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "개발실",
                                  "type": "GROUP",
                                  "memberUserIds": ["user02", "user03"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("개발실"))
                .andExpect(jsonPath("$.data.members.length()").value(3));

        mockMvc.perform(get("/api/v1/channels")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("개발실"));
    }

    @Test
    void getMessagesShouldReturnCursorSlice() throws Exception {
        String accessToken = loginAndGetAccessToken("user01");
        ChannelResponse channel = channelService.createChannel("user01",
                new CreateChannelRequest("백엔드", ChannelType.GROUP, java.util.List.of("user02")));

        chatService.saveMessage("user01", new ChatMessageRequest(channel.id(), "첫 번째", MessageType.TEXT, null));
        chatService.saveMessage("user01", new ChatMessageRequest(channel.id(), "두 번째", MessageType.TEXT, null));
        chatService.saveMessage("user01", new ChatMessageRequest(channel.id(), "세 번째", MessageType.TEXT, null));

        String response = mockMvc.perform(get("/api/v1/channels/{channelId}/messages", channel.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        MessageSliceResponse slice = objectMapper.readTree(response).has("data")
                ? objectMapper.treeToValue(objectMapper.readTree(response).get("data"), MessageSliceResponse.class)
                : null;

        assertThat(slice).isNotNull();
        assertThat(slice.nextCursor()).isNotNull();

        mockMvc.perform(get("/api/v1/channels/{channelId}/messages", channel.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("size", "2")
                        .param("cursor", String.valueOf(slice.nextCursor())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void directMessageShouldRequireExactlyTwoMembers() {
        assertThatThrownBy(() -> channelService.createChannel(
                "user01",
                new CreateChannelRequest(null, ChannelType.DM, java.util.List.of("user01"))
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CHANNEL);
    }

    @Test
    void markReadShouldRejectMessageFromAnotherChannel() {
        ChannelResponse firstChannel = channelService.createChannel("user01",
                new CreateChannelRequest("첫 번째", ChannelType.GROUP, java.util.List.of("user02")));
        ChannelResponse secondChannel = channelService.createChannel("user01",
                new CreateChannelRequest("두 번째", ChannelType.GROUP, java.util.List.of("user02")));
        chatService.saveMessage("user01",
                new ChatMessageRequest(secondChannel.id(), "다른 채널 메시지", MessageType.TEXT, null));
        Long otherChannelMessageId = messageRepository.findAll().getFirst().getId();

        assertThatThrownBy(() -> channelService.markRead("user01", firstChannel.id(), otherChannelMessageId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.MESSAGE_NOT_FOUND);
    }

    @Test
    void userShouldNotBeAbleToSendSystemMessage() {
        ChannelResponse channel = channelService.createChannel("user01",
                new CreateChannelRequest("운영", ChannelType.GROUP, java.util.List.of("user02")));

        assertThatThrownBy(() -> chatService.saveMessage("user01",
                new ChatMessageRequest(channel.id(), "위조 공지", MessageType.SYSTEM, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_MESSAGE);
    }

    private String loginAndGetAccessToken(String userId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "%s",
                                  "password": "password"
                                }
                                """.formatted(userId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonTestUtils.readJson(response, "$.data.accessToken");
    }

    private void ensureUser(String userId) {
        userRepository.findByUserId(userId).orElseGet(() -> userRepository.save(User.create(userId)));
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        @Primary
        SessionRegistry sessionRegistry() {
            return new InMemorySessionRegistry();
        }

        @Bean
        @Primary
        RefreshTokenStore refreshTokenStore() {
            return new InMemoryRefreshTokenStore();
        }

        @Bean
        @Primary
        SessionExpiryNotifier sessionExpiryNotifier() {
            return userId -> {
            };
        }
    }

    static class InMemorySessionRegistry implements SessionRegistry {
        private final Map<String, String> sessions = new ConcurrentHashMap<>();

        @Override
        public Optional<String> findSessionId(String userId) {
            return Optional.ofNullable(sessions.get(userId));
        }

        @Override
        public void save(String userId, String sessionId) {
            sessions.put(userId, sessionId);
        }

        @Override
        public void delete(String userId) {
            sessions.remove(userId);
        }
    }

    static class InMemoryRefreshTokenStore implements RefreshTokenStore {
        private final Map<String, String> tokens = new ConcurrentHashMap<>();

        @Override
        public void save(String userId, String refreshToken, Duration ttl) {
            tokens.put(userId, refreshToken);
        }

        @Override
        public Optional<String> find(String userId) {
            return Optional.ofNullable(tokens.get(userId));
        }

        @Override
        public void delete(String userId) {
            tokens.remove(userId);
        }
    }
}
