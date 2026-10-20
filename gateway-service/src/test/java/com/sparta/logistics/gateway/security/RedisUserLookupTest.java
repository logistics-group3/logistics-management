package com.sparta.logistics.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sparta.logistics.common.auth.UserAuthCacheKeys;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class RedisUserLookupTest {

    private static final Duration TTL = Duration.ofSeconds(60);

    @Mock
    private ReactiveRedisOperations<String, UserSnapshot> redis;
    @Mock
    private ReactiveValueOperations<String, UserSnapshot> values;

    private final UUID userId = UUID.randomUUID();
    private final String key = UserAuthCacheKeys.of(userId);
    private final UserSnapshot stored = new UserSnapshot(userId, "hong01", "USER", false);
    private final AtomicInteger loads = new AtomicInteger();

    @BeforeEach
    void setUp() {
        given(redis.opsForValue()).willReturn(values);
    }

    private RedisUserLookup lookup(Optional<UserSnapshot> loaded) {
        return new RedisUserLookup(redis, id -> {
            loads.incrementAndGet();
            return Mono.just(loaded);
        }, TTL);
    }

    @Test
    @DisplayName("Redis에 있으면 user-service를 부르지 않고 저장된 값을 쓴다")
    void hit() {
        given(values.get(key)).willReturn(Mono.just(stored));

        StepVerifier.create(lookup(Optional.empty()).find(userId)).expectNext(Optional.of(stored)).verifyComplete();

        assertThat(loads).hasValue(0);
    }

    @Test
    @DisplayName("Redis에 없으면 I-01로 조회해 TTL과 함께 저장한다")
    void missLoadsAndStores() {
        given(values.get(key)).willReturn(Mono.empty());
        given(values.set(key, stored, TTL)).willReturn(Mono.just(true));

        StepVerifier.create(lookup(Optional.of(stored)).find(userId)).expectNext(Optional.of(stored)).verifyComplete();

        assertThat(loads).hasValue(1);
        verify(values).set(key, stored, TTL);
    }

    @Test
    @DisplayName("없는 사용자(탈퇴)는 저장하지 않는다")
    void notFoundIsNotStored() {
        given(values.get(key)).willReturn(Mono.empty());

        StepVerifier.create(lookup(Optional.empty()).find(userId)).expectNext(Optional.empty()).verifyComplete();

        verify(values, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    @DisplayName("Redis 조회·저장이 실패해도 I-01 결과로 인증을 이어 간다")
    void redisFailureFallsBackToLoader() {
        given(values.get(key)).willReturn(Mono.error(new RedisConnectionFailureException("down")));
        given(values.set(eq(key), eq(stored), eq(TTL))).willReturn(Mono.error(new RedisConnectionFailureException("down")));

        StepVerifier.create(lookup(Optional.of(stored)).find(userId)).expectNext(Optional.of(stored)).verifyComplete();

        assertThat(loads).hasValue(1);
    }
}
