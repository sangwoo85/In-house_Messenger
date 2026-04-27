package com.company.messenger.global.external;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
@RequiredArgsConstructor
public class InternalAuthClient {

    private final WebClient internalAuthWebClient;

    public boolean authenticate(String userId, String password) {
        try {
            return internalAuthWebClient.post()
                    .uri("/login")
                    .bodyValue(new InternalAuthRequest(userId, password))
                    .retrieve()
                    .toBodilessEntity()
                    .map(response -> response.getStatusCode().is2xxSuccessful())
                    .blockOptional()
                    .orElse(false);
        } catch (WebClientResponseException exception) {
            HttpStatusCode statusCode = exception.getStatusCode();
            if (statusCode.is4xxClientError()) {
                return false;
            }
            throw exception;
        }
    }

    private record InternalAuthRequest(String userId, String password) {
    }
}

