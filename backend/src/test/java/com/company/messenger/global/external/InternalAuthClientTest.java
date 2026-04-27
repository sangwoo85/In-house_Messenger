package com.company.messenger.global.external;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

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
                WebClient.builder().baseUrl(server.url("/").toString()).build()
        );

        assertThat(client.authenticate("user01", "password")).isTrue();
    }

    @Test
    void authenticateReturnsFalseForUnauthorizedStatus() {
        server.enqueue(new MockResponse().setResponseCode(401));

        InternalAuthClient client = new InternalAuthClient(
                WebClient.builder().baseUrl(server.url("/").toString()).build()
        );

        assertThat(client.authenticate("user01", "wrong-password")).isFalse();
    }
}
