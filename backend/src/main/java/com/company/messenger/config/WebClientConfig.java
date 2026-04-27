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

    @Bean
    public WebClient internalAuthWebClient(
            WebClient.Builder builder,
            ExternalAuthProperties externalAuthProperties
    ) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(externalAuthProperties.authTimeoutSeconds()))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, externalAuthProperties.authTimeoutSeconds() * 1000);

        return builder
                .baseUrl(externalAuthProperties.authBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}

