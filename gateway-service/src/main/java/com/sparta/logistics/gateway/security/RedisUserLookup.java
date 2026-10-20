package com.sparta.logistics.gateway.security;

import com.sparta.logistics.common.auth.UserAuthCacheKeys;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import reactor.core.publisher.Mono;

/**
 * I-01 결과를 Redis에 캐시한다(auth.user-cache=redis). 모든 Gateway 인스턴스가 같은 캐시를 보고,
 * user-service가 권한 변경·탈퇴 때 키를 지워 다음 요청부터 바로 반영된다. TTL은 삭제가 빠졌을 때의 안전망이다.
 * Note: Redis는 성능 계층이라 장애가 인증 장애가 되면 안 된다. 조회·저장이 실패하면 I-01을 직접 부르는 것으로 넘어간다.
 * 없는 사용자(탈퇴)는 저장하지 않는다. 그 토큰은 매번 I-01에서 404를 받아 401로 끝난다.
 */
@Slf4j
@RequiredArgsConstructor
public class RedisUserLookup implements UserLookup {

    private final ReactiveRedisOperations<String, UserSnapshot> redis;
    private final Function<UUID, Mono<Optional<UserSnapshot>>> loader;
    private final Duration ttl;

    @Override
    public Mono<Optional<UserSnapshot>> find(UUID userId) {
        String key = UserAuthCacheKeys.of(userId);
        return redis.opsForValue().get(key)
                .map(Optional::of)
                .onErrorResume(e -> {
                    log.warn("Redis 권한 캐시 조회 실패, I-01로 대신한다: {}", e.toString());
                    return Mono.empty();
                })
                .switchIfEmpty(Mono.defer(() -> loader.apply(userId)
                        .flatMap(found -> store(key, found).thenReturn(found))));
    }

    private Mono<Boolean> store(String key, Optional<UserSnapshot> found) {
        if (found.isEmpty()) {
            return Mono.just(false);
        }
        return redis.opsForValue().set(key, found.get(), ttl)
                .onErrorResume(e -> {
                    log.warn("Redis 권한 캐시 저장 실패: {}", e.toString());
                    return Mono.just(false);
                });
    }
}
