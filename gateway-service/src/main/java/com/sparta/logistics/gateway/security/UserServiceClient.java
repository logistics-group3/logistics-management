package com.sparta.logistics.gateway.security;

import java.time.Duration;
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
    private final Duration timeout;

    public UserServiceClient(WebClient webClient, Duration timeout) {
        this.webClient = webClient;
        this.timeout = timeout;
    }

    public Mono<Optional<UserSnapshot>> fetch(UUID userId) {
        return webClient.get()
                .uri("/internal/users/{userId}", userId)
                .retrieve()
                .bodyToMono(TYPE)
                .map(envelope -> Optional.ofNullable(envelope.data()))
                // 연결·응답 타임아웃과 별개로, 인스턴스 선택부터 본문 해석까지 전체에 상한을 둔다
                .timeout(timeout)
                .onErrorResume(WebClientResponseException.class, e -> e.getStatusCode() == HttpStatus.NOT_FOUND
                        ? Mono.just(Optional.empty())
                        : Mono.error(e));
    }

    record ApiEnvelope<T>(int status, String message, T data) {
    }
}
