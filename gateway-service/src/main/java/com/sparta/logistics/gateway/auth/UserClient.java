package com.sparta.logistics.gateway.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * I-01 GET /internal/users/{userId} 호출. 탈퇴(논리 삭제)했거나 없는 사용자는 404 → empty.
 * 조회에 성공한 사용자는 userId 기준 60초 캐시한다 (권한 변경·탈퇴는 최대 60초 늦게 반영).
 * 없는 사용자는 캐시하지 않는다.
 */
@Component
public class UserClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final long CACHE_MAX_SIZE = 10_000;

    private final WebClient webClient;
    private final Cache<UUID, InternalUser> cache = Caffeine.newBuilder()
            .expireAfterWrite(CACHE_TTL)
            .maximumSize(CACHE_MAX_SIZE)
            .build();

    public UserClient(WebClient.Builder loadBalancedWebClientBuilder) {
        this.webClient = loadBalancedWebClientBuilder.baseUrl("lb://user-service").build();
    }

    public Mono<InternalUser> findUser(UUID userId) {
        InternalUser cached = cache.getIfPresent(userId);
        if (cached != null) {
            return Mono.just(cached);
        }
        return webClient.get()
                .uri("/internal/users/{userId}", userId)
                .retrieve()
                .bodyToMono(InternalUser.class)
                .timeout(TIMEOUT)
                .doOnNext(user -> cache.put(userId, user))
                .onErrorResume(WebClientResponseException.class,
                        e -> e.getStatusCode() == HttpStatus.NOT_FOUND ? Mono.empty() : Mono.error(e));
    }

    public record InternalUser(UUID userId, String username, String role, boolean isDeliveryManager) {
    }
}
