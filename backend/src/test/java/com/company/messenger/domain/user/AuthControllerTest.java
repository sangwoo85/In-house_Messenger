package com.company.messenger.domain.user;

import com.company.messenger.global.auth.RefreshTokenStore;
import com.company.messenger.global.auth.SessionExpiryNotifier;
import com.company.messenger.global.auth.SessionRegistry;
import com.company.messenger.global.external.InternalAuthClient;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionRegistry sessionRegistry;

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @MockitoBean
    private InternalAuthClient internalAuthClient;

    @MockitoBean
    private PresenceService presenceService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.when(internalAuthClient.fetchUsers()).thenReturn(java.util.List.of(
                new InternalAuthClient.ExternalDirectoryUser("user01", "user01", null, "개발팀", "사용자"),
                new InternalAuthClient.ExternalDirectoryUser("user02", "user02", null, "개발팀", "사용자"),
                new InternalAuthClient.ExternalDirectoryUser("user03", "user03", null, "개발팀", "사용자")));

        sessionRegistry.delete("user01");
        refreshTokenStore.delete("user01");
        when(presenceService.getPresence(org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(invocation -> {
                    java.util.List<String> userIds = invocation.getArgument(0);
                    return userIds.stream()
                            .map(userId -> new PresenceResponse(userId, UserStatus.OFFLINE))
                            .toList();
                });
    }

    @Test
    void loginShouldIssueAccessTokenAndRefreshCookie() throws Exception {
        when(internalAuthClient.login("user01", "password")).thenReturn(
                new InternalAuthClient.ExternalDirectoryUser("user01", "로그인 응답 이름", null, "인사부", "직원", "HR"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "emprId": "user01",
                                  "password": "password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.user.userId").value("user01"))
                .andExpect(jsonPath("$.data.user.nickname").value("로그인 응답 이름"))
                .andExpect(jsonPath("$.data.user.department").value("인사부"))
                .andExpect(jsonPath("$.data.user.departmentId").value("HR"))
                .andExpect(cookie().exists(AuthService.REFRESH_COOKIE_NAME));
    }

    @Test
    void refreshShouldIssueNewAccessTokenWhenRefreshCookieIsValid() throws Exception {
        when(internalAuthClient.login("user01", "password")).thenReturn(
                new InternalAuthClient.ExternalDirectoryUser("user01", "로그인 응답 이름", null, "인사부", "직원", "HR"));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "emprId": "user01",
                                  "password": "password"
                                }
                                """))
                .andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie(AuthService.REFRESH_COOKIE_NAME);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(cookie().exists(AuthService.REFRESH_COOKIE_NAME));
    }

    @Test
    void usersMeShouldRequireValidBearerToken() throws Exception {
        when(internalAuthClient.login("user01", "password")).thenReturn(
                new InternalAuthClient.ExternalDirectoryUser("user01", "로그인 응답 이름", null, "인사부", "직원", "HR"));

        String accessToken = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "emprId": "user01",
                                  "password": "password"
                                }
                                """))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String bearerToken = JsonTestUtils.readJson(accessToken, "$.data.accessToken");

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("user01"));
    }

    @Test
    void secondLoginShouldReplaceSessionAndInvalidatePreviousAccessToken() throws Exception {
        when(internalAuthClient.login(anyString(), anyString())).thenAnswer(invocation ->
                new InternalAuthClient.ExternalDirectoryUser(invocation.getArgument(0), "로그인 응답 이름", null, "인사부", "직원", "HR"));

        String firstResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "emprId": "user01",
                                  "password": "password"
                                }
                                """))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String firstToken = JsonTestUtils.readJson(firstResponse, "$.data.accessToken");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "emprId": "user01",
                                  "password": "password"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usersEndpointShouldReturnDirectoryFromExternalSource() throws Exception {
        when(internalAuthClient.login("user01", "password")).thenReturn(
                new InternalAuthClient.ExternalDirectoryUser("user01", "로그인 응답 이름", null, "인사부", "직원", "HR"));
        when(internalAuthClient.fetchUsers()).thenReturn(java.util.List.of(
                new InternalAuthClient.ExternalDirectoryUser("user01", "홍길동", null, "영업팀", "사용자"),
                new InternalAuthClient.ExternalDirectoryUser("user02", "김개발", null, "개발팀", "개발자")
        ));

        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "emprId": "user01",
                                  "password": "password"
                                }
                                """))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String bearerToken = JsonTestUtils.readJson(loginResponse, "$.data.accessToken");

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].userId").value("user02"))
                .andExpect(jsonPath("$.data[0].nickname").value("김개발"))
                .andExpect(jsonPath("$.data[0].department").value("개발팀"))
                .andExpect(jsonPath("$.data[0].userGroup").value("개발자"));
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
