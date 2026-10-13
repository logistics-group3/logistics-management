package com.sparta.logistics.gateway.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sparta.logistics.gateway.config.GatewayAuthProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * 권한 재확인용 user-service 내부 API(I-01) 호출 + 짧은 TTL 로컬 캐시 (SA 8.1, 8.2).
 * <ul>
 *   <li>FOUND·NOT_FOUND 결과는 roleCacheTtl(기본 60초) 동안 캐시 → 권한 변경은 최대 TTL만큼 늦게 반영</li>
 *   <li>UNAVAILABLE(타임아웃·5xx)은 캐시하지 않는다</li>
 * </ul>
 */
@Slf4j
@Component
public class UserLookupClient {

    private final WebClient webClient;
    private final GatewayAuthProperties properties;
    private final Clock clock;
    private final Map<UUID, CachedLookup> cache = new ConcurrentHashMap<>();

    @Autowired
    public UserLookupClient(WebClient.Builder loadBalancedWebClientBuilder, GatewayAuthProperties properties) {
        this(loadBalancedWebClientBuilder.baseUrl("http://user-service").build(), properties, Clock.systemUTC());
    }

    UserLookupClient(WebClient webClient, GatewayAuthProperties properties, Clock clock) {
        this.webClient = webClient;
        this.properties = properties;
        this.clock = clock;
    }

    /** 항상 값 하나를 반환한다 (FOUND / NOT_FOUND / UNAVAILABLE) */
    public Mono<UserLookup> lookup(UUID userId) {
        CachedLookup cached = cache.get(userId);
        if (cached != null && cached.expiresAt().isAfter(clock.instant())) {
            return Mono.just(cached.lookup());
        }
        return fetch(userId).doOnNext(result -> {
            if (result.status() != Status.UNAVAILABLE) {
                cache.put(userId, new CachedLookup(result, clock.instant().plus(properties.getRoleCacheTtl())));
            }
        });
    }

    private Mono<UserLookup> fetch(UUID userId) {
        return webClient.get()
                .uri("/internal/users/{userId}", userId)
                .retrieve()
                .bodyToMono(InternalUser.class)
                .timeout(properties.getUserLookupTimeout())
                .map(UserLookup::found)
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.just(UserLookup.notFound()))
                .onErrorResume(e -> {
                    log.warn("사용자 권한 조회 실패 userId={}: {}", userId, e.getMessage());
                    return Mono.just(UserLookup.unavailable());
                })
                .defaultIfEmpty(UserLookup.notFound());
    }

    /** I-01 응답 중 Gateway가 쓰는 필드 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InternalUser(
            UUID userId,
            String username,
            String role,
            @JsonProperty("isDeliveryManager") boolean deliveryManager
    ) {
    }

    public enum Status { FOUND, NOT_FOUND, UNAVAILABLE }

    public record UserLookup(Status status, InternalUser user) {

        static UserLookup found(InternalUser user) {
            return new UserLookup(Status.FOUND, user);
        }

        static UserLookup notFound() {
            return new UserLookup(Status.NOT_FOUND, null);
        }

        static UserLookup unavailable() {
            return new UserLookup(Status.UNAVAILABLE, null);
        }
    }

    private record CachedLookup(UserLookup lookup, Instant expiresAt) {
    }
}
