package com.company.messenger.global.external;

import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;

@Component
@RequiredArgsConstructor
public class InternalAuthClient {

    private final WebClient internalAuthWebClient;
    private final ExternalAuthProperties externalAuthProperties;

    public boolean authenticate(String userId, String password) {
        try {
            return internalAuthWebClient.post()
                    .uri(externalAuthProperties.authLoginPath())
                    .contentType(MediaType.APPLICATION_JSON)
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

    public List<ExternalDirectoryUser> fetchUsers() {
        return internalAuthWebClient.get()
                .uri(externalAuthProperties.userListPath())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<InternalDirectoryUserResponse>>() {
                })
                .map(users -> users.stream()
                        .map(user -> new ExternalDirectoryUser(user.id(), user.name(), null))
                        .toList())
                .blockOptional()
                .orElse(List.of());
    }

    public record ExternalDirectoryUser(
            String userId,
            String nickname,
            String profileImageUrl
    ) {
    }

    private record InternalAuthRequest(String id, String password) {
    }

    private record InternalDirectoryUserResponse(
            String id,
            String name
    ) {
    }
}
