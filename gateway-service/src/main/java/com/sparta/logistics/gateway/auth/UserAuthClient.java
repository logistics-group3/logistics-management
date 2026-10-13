package com.sparta.logistics.gateway.auth;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.gateway.config.GatewayAuthProperties;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 권한 재확인 (SA 8.2, 8.3) — user-service I-01 호출 + 60초 캐시
 * - 사용자 없음(탈퇴·논리 삭제 포함) → UserNotFoundException → 401 TOKEN_ROLE_MISMATCH
 * - user-service 장애 → 그 외 예외 → 503 SERVICE_UNAVAILABLE
 * 실패한 결과는 캐시에 남지 않는다 (Caffeine AsyncCache 가 실패한 future 를 자동 제거).
 */
@Slf4j
@Component
public class UserAuthClient {

    private static final ParameterizedTypeReference<ApiResponse<UserAuthInfo>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient webClient;
    private final AsyncCache<UUID, UserAuthInfo> cache;

    /**
     * @param webClientBuilder Spring Boot 기본 WebClient.Builder (요청마다 새 인스턴스)
     * @param loadBalancer     http://user-service 를 Eureka 인스턴스 주소로 바꿔주는 필터
     *                         (전역 @LoadBalanced Builder 빈을 만들지 않아 다른 컴포넌트에 영향이 없다)
     */
    public UserAuthClient(WebClient.Builder webClientBuilder,
                          ReactorLoadBalancerExchangeFilterFunction loadBalancer,
                          GatewayAuthProperties properties) {
        this.webClient = webClientBuilder
                .baseUrl("http://user-service")
                .filter(loadBalancer)
                .build();
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(properties.roleCacheTtl())
                .maximumSize(10_000)
                .buildAsync();
    }

    public Mono<UserAuthInfo> getUser(UUID userId) {
        // suppressCancel=true: 한 클라이언트가 연결을 끊어도 캐시에 공유된 조회(future)는 취소하지 않는다
        return Mono.fromFuture(() -> cache.get(userId, (id, executor) -> fetch(id).toFuture()), true);
    }

    private Mono<UserAuthInfo> fetch(UUID userId) {
        return webClient.get()
                .uri("/internal/users/{userId}", userId) // ← 여기서 호출
                .retrieve()
                .onStatus(status -> status.value() == HttpStatus.NOT_FOUND.value(),
                        response -> Mono.error(new UserNotFoundException()))
                .bodyToMono(RESPONSE_TYPE)
                .flatMap(body -> Mono.justOrEmpty(body.data()))
                .switchIfEmpty(Mono.error(new UserNotFoundException()))
                .doOnError(e -> {
                    if (!(e instanceof UserNotFoundException)) {
                        log.error("user-service I-01 call failed. userId={}", userId, e);
                    }
                });
    }

    public static class UserNotFoundException extends RuntimeException {
    }
}
