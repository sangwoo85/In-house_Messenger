package com.company.messenger.global.external;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InternalAuthClientTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void authenticateReturnsTrueForSuccessStatus() {
        server.enqueue(new MockResponse().setResponseCode(200));

        InternalAuthClient client = new InternalAuthClient(
                WebClient.builder().baseUrl(server.url("/").toString()).build(),
                new ExternalAuthProperties(server.url("/").toString(), 3, "/api/v1/login", "/api/v1/getUserList")
        );

        assertThat(client.authenticate("user01", "password")).isTrue();
    }

    @Test
    void authenticateReturnsFalseForUnauthorizedStatus() {
        server.enqueue(new MockResponse().setResponseCode(401));

        InternalAuthClient client = new InternalAuthClient(
                WebClient.builder().baseUrl(server.url("/").toString()).build(),
                new ExternalAuthProperties(server.url("/").toString(), 3, "/api/v1/login", "/api/v1/getUserList")
        );

        assertThat(client.authenticate("user01", "wrong-password")).isFalse();
    }

    @Test
    void fetchUsersMapsDummyDirectoryResponse() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("""
                        [
                          { "id": "user01", "name": "홍길동" },
                          { "id": "user02", "name": "김개발" }
                        ]
                        """)
                .addHeader("Content-Type", "application/json"));

        InternalAuthClient client = new InternalAuthClient(
                WebClient.builder().baseUrl(server.url("/").toString()).build(),
                new ExternalAuthProperties(server.url("/").toString(), 3, "/api/v1/login", "/api/v1/getUserList")
        );

        List<InternalAuthClient.ExternalDirectoryUser> users = client.fetchUsers();

        assertThat(users).containsExactly(
                new InternalAuthClient.ExternalDirectoryUser("user01", "홍길동", null),
                new InternalAuthClient.ExternalDirectoryUser("user02", "김개발", null)
        );
    }
}
