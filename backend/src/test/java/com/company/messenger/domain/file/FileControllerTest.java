package com.company.messenger.domain.file;

import com.company.messenger.domain.user.JsonTestUtils;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.file.storage-path=target/test-uploads",
        "app.file.image-max-bytes=10",
        "app.file.other-max-bytes=20"
})
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileAttachmentRepository fileAttachmentRepository;

    @MockBean
    private InternalAuthClient internalAuthClient;

    @BeforeEach
    void setUp() throws Exception {
        when(internalAuthClient.authenticate(anyString(), anyString())).thenReturn(true);
        fileAttachmentRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(User.create("user01"));

        Path uploadDir = Path.of("target/test-uploads");
        if (Files.exists(uploadDir)) {
            try (var paths = Files.walk(uploadDir)) {
                paths.sorted((left, right) -> right.getNameCount() - left.getNameCount())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (Exception ignored) {
                            }
                        });
            }
        }
    }

    @Test
    void uploadAndDownloadImageShouldSucceed() throws Exception {
        String accessToken = loginAndGetAccessToken();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.png",
                "image/png",
                "1234567890".getBytes()
        );

        String response = mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalName").value("sample.png"))
                .andExpect(jsonPath("$.data.image").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String fileId = JsonTestUtils.readJson(response, "$.data.id");

        mockMvc.perform(get("/api/v1/files/{fileId}", fileId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("sample.png")));
    }

    @Test
    void uploadShouldRejectOversizedFile() throws Exception {
        String accessToken = loginAndGetAccessToken();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.pdf",
                "application/pdf",
                "123456789012345678901".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_002"));
    }

    private String loginAndGetAccessToken() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
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

        return JsonTestUtils.readJson(response, "$.data.accessToken");
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
