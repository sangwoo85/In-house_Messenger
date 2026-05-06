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
import org.springframework.boot.test.mock.mockito.MockBean;
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

    @MockBean
    private InternalAuthClient internalAuthClient;

    @BeforeEach
    void setUp() {
        sessionRegistry.delete("user01");
        refreshTokenStore.delete("user01");
    }

    @Test
    void loginShouldIssueAccessTokenAndRefreshCookie() throws Exception {
        when(internalAuthClient.authenticate("user01", "password")).thenReturn(true);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "user01",
                                  "password": "password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.user.userId").value("user01"))
                .andExpect(cookie().exists(AuthService.REFRESH_COOKIE_NAME));
    }

    @Test
    void refreshShouldIssueNewAccessTokenWhenRefreshCookieIsValid() throws Exception {
        when(internalAuthClient.authenticate("user01", "password")).thenReturn(true);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "user01",
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
        when(internalAuthClient.authenticate("user01", "password")).thenReturn(true);

        String accessToken = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "user01",
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
        when(internalAuthClient.authenticate(anyString(), anyString())).thenReturn(true);

        String firstResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "user01",
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
                                  "userId": "user01",
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
        when(internalAuthClient.authenticate("user01", "password")).thenReturn(true);
        when(internalAuthClient.fetchUsers()).thenReturn(java.util.List.of(
                new InternalAuthClient.ExternalDirectoryUser("user01", "홍길동", null),
                new InternalAuthClient.ExternalDirectoryUser("user02", "김개발", null)
        ));

        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "user01",
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
                .andExpect(jsonPath("$.data[0].nickname").value("김개발"));
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
