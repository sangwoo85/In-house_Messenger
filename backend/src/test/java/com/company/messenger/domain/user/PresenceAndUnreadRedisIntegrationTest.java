package com.company.messenger.domain.user;

import com.company.messenger.domain.channel.*;
import com.company.messenger.domain.message.ChatMessageRequest;
import com.company.messenger.domain.message.ChatService;
import com.company.messenger.domain.message.MessageRepository;
import com.company.messenger.domain.message.MessageType;
import com.company.messenger.global.auth.RefreshTokenStore;
import com.company.messenger.global.auth.SessionExpiryNotifier;
import com.company.messenger.global.auth.SessionRegistry;
import com.company.messenger.global.external.InternalAuthClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PresenceAndUnreadRedisIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private ChannelMemberRepository channelMemberRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ChatService chatService;

    @Autowired
    private ChannelService channelService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockBean
    private InternalAuthClient internalAuthClient;

    @BeforeEach
    void setUp() {
        when(internalAuthClient.authenticate(anyString(), anyString())).thenReturn(true);

        messageRepository.deleteAll();
        channelMemberRepository.deleteAll();
        channelRepository.deleteAll();
        userRepository.deleteAll();
        ensureUser("user01");
        ensureUser("user02");

        Set<String> keys = redisTemplate.keys("presence:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        keys = redisTemplate.keys("unread:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void heartbeatAndPresenceLookupShouldUseRedis() throws Exception {
        String accessToken = loginAndGetAccessToken("user01");

        mockMvc.perform(post("/api/v1/users/presence/heartbeat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "AWAY"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/presence")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("userIds", "user01", "user02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value("user01"))
                .andExpect(jsonPath("$.data[0].status").value("AWAY"))
                .andExpect(jsonPath("$.data[1].status").value("OFFLINE"));
    }

    @Test
    void unreadCountShouldIncreaseAndResetAfterRead() throws Exception {
        String user01Token = loginAndGetAccessToken("user01");
        String user02Token = loginAndGetAccessToken("user02");
        ChannelResponse channel = channelService.createChannel("user01",
                new CreateChannelRequest("운영", ChannelType.GROUP, java.util.List.of("user02")));

        chatService.saveMessage("user01", new ChatMessageRequest(channel.id(), "미확인 메시지", MessageType.TEXT, null));

        String response = mockMvc.perform(get("/api/v1/channels")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user02Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].unreadCount").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long messageId = messageRepository.findAll().getFirst().getId();

        mockMvc.perform(patch("/api/v1/channels/{channelId}/read", channel.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user02Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messageId": %d
                                }
                                """.formatted(messageId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/channels")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user02Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].unreadCount").value(0));
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
