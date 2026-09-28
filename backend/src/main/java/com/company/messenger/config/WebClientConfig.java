package com.company.messenger.config;

import com.company.messenger.global.external.ExternalAuthProperties;
import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    /**
     * Builds the business API client with bounded connection, response time and directory response size.
     * @param builder shared HTTP client builder
     * @param externalAuthProperties site endpoint and timeout settings
     * @param maxResponseBytes maximum buffered JSON response size, including organization directories
     * @return a client dedicated to the configured business API
     */
    @Bean
    public WebClient internalAuthWebClient(
            WebClient.Builder builder,
            ExternalAuthProperties externalAuthProperties,
            @org.springframework.beans.factory.annotation.Value("${app.external.max-response-bytes:4194304}") int maxResponseBytes
    ) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(externalAuthProperties.authTimeoutSeconds()))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, externalAuthProperties.authTimeoutSeconds() * 1000);

        return builder
                .baseUrl(externalAuthProperties.authBaseUrl())
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(maxResponseBytes))
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
