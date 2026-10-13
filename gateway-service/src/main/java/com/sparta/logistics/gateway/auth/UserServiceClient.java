package com.sparta.logistics.gateway.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * user-service 내부 API(I-01) 호출. Gateway는 WebFlux 기반이라 블로킹 Feign 대신 WebClient를 쓴다.
 */
public class UserServiceClient {

    private static final ParameterizedTypeReference<ApiEnvelope<UserSnapshot>> TYPE = new ParameterizedTypeReference<>() {
    };

    private final WebClient webClient;

    public UserServiceClient(WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<Optional<UserSnapshot>> fetch(UUID userId) {
        return webClient.get()
                .uri("/internal/users/{userId}", userId)
                .retrieve()
                .bodyToMono(TYPE)
                .map(envelope -> Optional.ofNullable(envelope.data()))
                .onErrorResume(WebClientResponseException.class, e -> e.getStatusCode() == HttpStatus.NOT_FOUND
                        ? Mono.just(Optional.empty())
                        : Mono.error(e));
    }

    record ApiEnvelope<T>(int status, String message, T data) {
    }
}
