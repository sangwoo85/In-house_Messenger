package com.company.messenger.domain.notice;

import com.company.messenger.domain.user.User;
import com.company.messenger.domain.user.UserRepository;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalNoticeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NoticeRepository noticeRepository;

    @Autowired
    private UserNotificationRepository userNotificationRepository;

    @MockBean
    private InternalAuthClient internalAuthClient;

    @MockBean
    private SimpMessagingTemplate simpMessagingTemplate;

    @BeforeEach
    void setUp() {
        when(internalAuthClient.authenticate(anyString(), anyString())).thenReturn(true);
        userNotificationRepository.deleteAll();
        noticeRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(User.create("user01"));
    }

    @Test
    void broadcastShouldValidateInternalApiKeyAndPersistNotice() throws Exception {
        mockMvc.perform(post("/api/v1/internal/notice/broadcast")
                        .header("X-Internal-Api-Key", "change-me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "공지 제목",
                                  "content": "공지 내용",
                                  "sender": "시스템"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("공지 제목"));

        assertThat(noticeRepository.count()).isEqualTo(1);
        verify(simpMessagingTemplate).convertAndSend(org.mockito.ArgumentMatchers.eq("/topic/notice"), org.mockito.ArgumentMatchers.any(NoticeResponse.class));
    }

    @Test
    void notifyUserAndReadFlowShouldPersistAndReturnNotifications() throws Exception {
        mockMvc.perform(post("/api/v1/internal/notify/user")
                        .header("X-Internal-Api-Key", "change-me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetUserId": "user01",
                                  "title": "결재 요청",
                                  "content": "휴가 신청 결재 요청이 왔습니다.",
                                  "linkUrl": "https://example.com/approval"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("결재 요청"));

        String accessToken = loginAndGetAccessToken();

        String listResponse = mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].read").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long notificationId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(listResponse)
                .get("data")
                .get("items")
                .get(0)
                .get("id")
                .asLong();

        mockMvc.perform(patch("/api/v1/notifications/{id}/read", notificationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].read").value(true));
    }

    @Test
    void invalidInternalApiKeyShouldBeRejected() throws Exception {
        mockMvc.perform(post("/api/v1/internal/notice/broadcast")
                        .header("X-Internal-Api-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "공지 제목",
                                  "content": "공지 내용",
                                  "sender": "시스템"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INTERNAL_001"));
    }

    private String loginAndGetAccessToken() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "user01",
                                  "password": "password"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response)
                .get("data")
                .get("accessToken")
                .asText();
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
