package com.company.messenger.domain.user;

import com.company.messenger.domain.organization.OrganizationSnapshotRepository;
import com.company.messenger.global.auth.RefreshTokenStore;
import com.company.messenger.global.auth.SessionExpiryNotifier;
import com.company.messenger.global.auth.SessionRegistry;
import com.company.messenger.global.exception.BusinessException;
import com.company.messenger.global.exception.ErrorCode;
import com.company.messenger.global.external.InternalAuthClient;
import com.company.messenger.global.external.InternalAuthClient.ExternalDepartment;
import com.company.messenger.global.external.InternalAuthClient.ExternalDirectoryUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:identity_features;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "app.file.storage-path=target/identity-profile-test-uploads",
        "app.external.legacy-user-api=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdentityProfileOrganizationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired OrganizationSnapshotRepository snapshots;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @MockitoBean InternalAuthClient external;
    @MockitoBean PresenceService presence;
    @MockitoBean SessionRegistry sessions;
    @MockitoBean RefreshTokenStore refreshTokens;
    @MockitoBean SessionExpiryNotifier expiry;
    private String token;
    private Map<String, String> activeSessions;

    @BeforeEach
    void setUp() throws Exception {
        snapshots.deleteAll();
        users.deleteAll();
        activeSessions = new ConcurrentHashMap<>();
        when(sessions.findSessionId(anyString())).thenAnswer(call -> Optional.ofNullable(activeSessions.get(call.getArgument(0))));
        doAnswer(call -> { activeSessions.put(call.getArgument(0), call.getArgument(1)); return null; }).when(sessions).save(anyString(), anyString());
        when(external.login(anyString(), anyString())).thenAnswer(call ->
                new ExternalDirectoryUser(call.getArgument(0), "로그인 이름", null, "개발실", "직원", "DEV"));
        when(external.fetchUsers()).thenReturn(List.of(
                new ExternalDirectoryUser("owner", "홍길동", null, "개발실", "직원", "DEV"),
                new ExternalDirectoryUser("coworker", "동료", null, "플랫폼팀", "직원", "PLATFORM")));
        when(external.fetchDepartments()).thenReturn(List.of(
                new ExternalDepartment("ROOT", null, "회사"), new ExternalDepartment("DEV", "ROOT", "개발실"),
                new ExternalDepartment("PLATFORM", "DEV", "플랫폼팀")));
        when(presence.getPresence(anyList())).thenAnswer(call -> ((List<String>) call.getArgument(0)).stream()
                .map(id -> new PresenceResponse(id, UserStatus.ONLINE)).toList());
        token = login("owner");
    }

    @Test
    void pngUploadIsAuthenticatedNormalizedAndSurvivesDirectoryRefresh() throws Exception {
        var upload = mvc.perform(multipart("/api/v1/users/me/profile-image")
                        .file(image("png", 16, 16)).with(request -> { request.setMethod("PUT"); return request; })
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.profileImageUrl").isString()).andReturn();
        String url = JsonTestUtils.readJson(upload.getResponse().getContentAsString(), "$.data.profileImageUrl");
        assertThat(url).startsWith("/api/v1/users/owner/profile-image?v=");
        String coworkerToken = login("coworker");
        mvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + coworkerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].profileImageUrl").value(url));
        byte[] bytes = mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + coworkerToken))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(bytes))).isNotNull();
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
    }

    @Test
    void replacementChangesUrlRemovesPreviousBytesAndDeleteRestoresDefaultAvatar() throws Exception {
        upload(image("jpeg", 20, 20));
        String oldKey = users.findByUserId("owner").orElseThrow().getProfileImageKey();
        upload(image("png", 20, 20));
        String newKey = users.findByUserId("owner").orElseThrow().getProfileImageKey();
        assertThat(newKey).isNotEqualTo(oldKey);
        assertThat(Files.exists(Path.of("target/identity-profile-test-uploads/profiles", oldKey + ".png"))).isFalse();
        mvc.perform(delete("/api/v1/users/me/profile-image").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.profileImageUrl").doesNotExist());
        mvc.perform(get("/api/v1/users/owner/profile-image").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
        assertThat(Files.exists(Path.of("target/identity-profile-test-uploads/profiles", newKey + ".png"))).isFalse();
    }

    @Test
    void concurrentDirectoryUpdateCannotRestoreADeletedImageKey() throws Exception {
        upload(image("png", 12, 12));
        String oldKey = users.findByUserId("owner").orElseThrow().getProfileImageKey();
        var readOldProfile = new java.util.concurrent.CountDownLatch(1);
        var continueSync = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var update = executor.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactions)
                    .executeWithoutResult(status -> {
                        User user = users.findByUserId("owner").orElseThrow();
                        user.syncProfile("새 이름", null, "새 부서", "직원");
                        readOldProfile.countDown();
                        try {
                            if (!continueSync.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                        }
                    }));
            try {
                assertThat(readOldProfile.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                upload(image("png", 14, 14));
            } finally {
                continueSync.countDown();
            }
            update.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        User user = users.findByUserId("owner").orElseThrow();
        assertThat(user.getNickname()).isEqualTo("새 이름");
        assertThat(user.getProfileImageKey()).isNotEqualTo(oldKey).isNotNull();
        assertThat(Files.exists(Path.of("target/identity-profile-test-uploads/profiles", user.getProfileImageKey() + ".png"))).isTrue();
    }

    @Test
    void forgedMimeGifExcessPixelsAndOversizedUploadsAreRejected() throws Exception {
        reject(new MockMultipartFile("file", "pretend.png", "image/png", "<svg/>".getBytes()));
        reject(image("gif", 8, 8));
        reject(image("png", 4097, 1));
        reject(new MockMultipartFile("file", "large.png", "image/png", new byte[5242881]));
        assertThat(users.findByUserId("owner").orElseThrow().getProfileImageKey()).isNull();
    }

    @Test
    void oneUserCannotOverwriteACoworkersImage() throws Exception {
        login("coworker");
        mvc.perform(multipart("/api/v1/users/coworker/profile-image").file(image("png", 8, 8))
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isMethodNotAllowed());
        assertThat(users.findByUserId("coworker").orElseThrow().getProfileImageKey()).isNull();
    }

    @Test
    void organizationTreeIsPersistedWithLivePresenceAndCachedWithinItsTtl() throws Exception {
        mvc.perform(get("/api/v1/organizations").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.departments[2].parentId").value("DEV"))
                .andExpect(jsonPath("$.data.users[0].departmentId").value("PLATFORM"))
                .andExpect(jsonPath("$.data.users[0].status").value("ONLINE"))
                .andExpect(jsonPath("$.data.stale").value(false));
        mvc.perform(get("/api/v1/organizations").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isOk());
        verify(external, times(1)).fetchUsers();
        verify(external, times(1)).fetchDepartments();
        assertThat(snapshots.count()).isEqualTo(1);
    }

    @Test
    void expiredSnapshotRemainsBrowsableDuringOutageButCannotAuthenticateAccounts() throws Exception {
        mvc.perform(get("/api/v1/organizations").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isOk());
        jdbc.update("update organization_snapshots set synced_at = ? where id = 1", java.time.LocalDateTime.now().minusHours(1));
        when(external.fetchUsers()).thenThrow(new BusinessException(ErrorCode.EXTERNAL_AUTH_UNAVAILABLE));
        when(external.login(anyString(), anyString())).thenThrow(new BusinessException(ErrorCode.EXTERNAL_AUTH_UNAVAILABLE));
        mvc.perform(get("/api/v1/organizations").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.stale").value(true))
                .andExpect(jsonPath("$.data.users[0].userId").value("coworker"));
        mvc.perform(get("/api/v1/organizations").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.stale").value(true));
        // The successful initial load plus one failed refresh; the next coworker uses the cooldown.
        verify(external, times(2)).fetchUsers();
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emprId\":\"owner\",\"password\":\"pw\"}")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void cyclicOrOrphanedOrganizationsAreRejectedWithoutReplacingTheSnapshot() throws Exception {
        when(external.fetchDepartments()).thenReturn(List.of(new ExternalDepartment("DEV", "DEV", "순환")));
        mvc.perform(get("/api/v1/organizations").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isServiceUnavailable());
        assertThat(snapshots.count()).isZero();
        when(external.fetchDepartments()).thenReturn(List.of(new ExternalDepartment("DEV", "MISSING", "고아")));
        mvc.perform(get("/api/v1/organizations").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isServiceUnavailable());
        assertThat(snapshots.count()).isZero();
    }

    private String login(String userId) throws Exception {
        var response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emprId\":\"" + userId + "\",\"password\":\"pw\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonTestUtils.readJson(response, "$.data.accessToken");
    }

    private MockMultipartFile image(String format, int width, int height) throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, output);
        return new MockMultipartFile("file", "avatar." + format, "application/octet-stream", output.toByteArray());
    }

    private void upload(MockMultipartFile file) throws Exception {
        mvc.perform(multipart("/api/v1/users/me/profile-image").file(file)
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isOk());
    }

    private void reject(MockMultipartFile file) throws Exception {
        mvc.perform(multipart("/api/v1/users/me/profile-image").file(file)
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isBadRequest());
    }
}
