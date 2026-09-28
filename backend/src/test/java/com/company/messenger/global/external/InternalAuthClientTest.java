package com.company.messenger.global.external;

import com.company.messenger.global.exception.BusinessException;
import com.company.messenger.global.exception.ErrorCode;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalAuthClientTest {
    private MockWebServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception { server.shutdown(); }

    @Test
    void loginSendsEmprIdAndUsesTheReturnedProfileWithoutRequestingTheDirectory() throws Exception {
        enqueue("{\"emprId\":\"E01\",\"userName\":\"홍길동\",\"departmentName\":\"개발실\",\"departmentId\":\"DEV\"}");
        var profile = client(false).login("E01", "password");
        assertThat(profile.nickname()).isEqualTo("홍길동");
        assertThat(profile.department()).isEqualTo("개발실");
        assertThat(profile.departmentId()).isEqualTo("DEV");
        assertThat(server.takeRequest().getBody().readUtf8()).contains("\"emprId\":\"E01\"", "\"password\":\"password\"");
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void loginMayOmitEmprIdWhenSuccessResponseContainsOnlyNameAndDepartment() {
        enqueue("{\"userName\":\"홍길동\",\"departmentName\":\"개발실\"}");
        assertThat(client(false).login("E01", "password").userId()).isEqualTo("E01");
    }

    @Test
    void loginRejectsUnauthorizedAndDoesNotReadLocalFallbackData() {
        server.enqueue(new MockResponse().setResponseCode(401));
        assertError(() -> client(false).login("E01", "bad"), ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void loginRejectsSuccessFalseEvenWhenHttpStatusIsSuccessful() {
        enqueue("{\"success\":false,\"userName\":\"홍길동\"}");
        assertError(() -> client(false).login("E01", "bad"), ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void loginRejectsAProfileForADifferentIdentity() {
        enqueue("{\"emprId\":\"OTHER\",\"userName\":\"다른 사용자\"}");
        assertError(() -> client(false).login("E01", "password"), ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void loginReportsMalformedSuccessBodyAsUnavailable() {
        enqueue("{}");
        assertError(() -> client(false).login("E01", "password"), ErrorCode.EXTERNAL_AUTH_UNAVAILABLE);
    }

    @Test
    void loginReportsServerErrorsAsUnavailable() {
        server.enqueue(new MockResponse().setResponseCode(500));
        assertError(() -> client(false).login("E01", "password"), ErrorCode.EXTERNAL_AUTH_UNAVAILABLE);
    }

    @Test
    void customFieldNamesAndNestedResponseRootsAreSupported() throws Exception {
        var fields = new ExternalApiFields("employee", "secret", "employee", "displayName", "deptLabel", "dept", "role",
                "data.user", "data.people", "data.departments", "code", "parent", "label", "result", "OK");
        var client = new InternalAuthClient(WebClient.builder().baseUrl(server.url("/").toString()).build(), properties(false), fields);
        enqueue("{\"result\":\"OK\",\"data\":{\"user\":{\"employee\":\"E01\",\"displayName\":\"사용자\",\"deptLabel\":\"인사부\"}}}");
        assertThat(client.login("E01", "pw").department()).isEqualTo("인사부");
        assertThat(server.takeRequest().getBody().readUtf8()).contains("\"employee\":\"E01\"", "\"secret\":\"pw\"");
        enqueue("{\"data\":{\"departments\":[{\"code\":\"ROOT\",\"label\":\"회사\",\"parent\":null}]}}");
        assertThat(client.fetchDepartments()).containsExactly(new InternalAuthClient.ExternalDepartment("ROOT", null, "회사"));
    }

    @Test
    void explicitLegacyAdapterUsesIdAndLoadsProfileFromDemoDirectory() throws Exception {
        enqueue("{\"message\":\"login success\"}");
        enqueue("[{\"id\":\"E01\",\"name\":\"사용자\",\"dept\":\"개발팀\",\"group\":\"개발자\"}]");
        var profile = client(true).login("E01", "pw");
        assertThat(profile.departmentId()).isEqualTo("개발팀");
        assertThat(server.takeRequest().getBody().readUtf8()).contains("\"id\":\"E01\"");
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void directoryIgnoresExternalImageUrlsAndMapsDepartmentIds() {
        enqueue("[{\"emprId\":\"E01\",\"userName\":\"사용자\",\"departmentId\":\"DEV\",\"profileImageUrl\":\"https://outside.invalid/pixel\"}]");
        var users = client(false).fetchUsers();
        assertThat(users).hasSize(1);
        assertThat(users.getFirst().departmentId()).isEqualTo("DEV");
        assertThat(users.getFirst().profileImageUrl()).isNull();
    }

    @Test
    void duplicateDirectoryIdentitiesAreRejected() {
        enqueue("[{\"emprId\":\"E01\",\"userName\":\"A\"},{\"emprId\":\"E01\",\"userName\":\"B\"}]");
        assertError(() -> client(false).fetchUsers(), ErrorCode.EXTERNAL_AUTH_UNAVAILABLE);
    }

    @Test
    void malformedDirectoryAndOrganizationPayloadsAreRejected() {
        enqueue("{}");
        assertError(() -> client(false).fetchUsers(), ErrorCode.EXTERNAL_AUTH_UNAVAILABLE);
        enqueue("[{\"id\":\"ROOT\"}]");
        assertError(() -> client(false).fetchDepartments(), ErrorCode.EXTERNAL_AUTH_UNAVAILABLE);
    }

    private InternalAuthClient client(boolean legacy) {
        return new InternalAuthClient(WebClient.builder().baseUrl(server.url("/").toString()).build(), properties(legacy));
    }

    private ExternalAuthProperties properties(boolean legacy) {
        return new ExternalAuthProperties(server.url("/").toString(), 3, "/login", "/users", "/departments", legacy, 300);
    }

    private void enqueue(String json) {
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(json));
    }

    private void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode()).isEqualTo(expected);
    }
}
