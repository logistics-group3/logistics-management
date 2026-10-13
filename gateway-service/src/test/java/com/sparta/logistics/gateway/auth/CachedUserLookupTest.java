package com.sparta.logistics.gateway.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class CachedUserLookupTest {

    private final UUID userId = UUID.randomUUID();
    private final AtomicInteger calls = new AtomicInteger();

    @Test
    @DisplayName("TTL 안에서는 같은 사용자를 다시 조회하지 않는다")
    void cachesWithinTtl() {
        UserSnapshot user = new UserSnapshot(userId, "hong01", "USER", false);
        CachedUserLookup lookup = new CachedUserLookup(id -> {
            calls.incrementAndGet();
            return Mono.just(Optional.of(user));
        }, Duration.ofSeconds(60));

        lookup.find(userId).block();
        Optional<UserSnapshot> second = lookup.find(userId).block();

        assertThat(second).contains(user);
        assertThat(calls).hasValue(1);
    }

    @Test
    @DisplayName("조회 실패는 캐시하지 않고 다음 요청에서 다시 조회한다")
    void doesNotCacheFailure() {
        CachedUserLookup lookup = new CachedUserLookup(id -> calls.incrementAndGet() == 1
                ? Mono.error(new IllegalStateException("down"))
                : Mono.just(Optional.empty()), Duration.ofSeconds(60));

        assertThatThrownBy(() -> lookup.find(userId).block()).hasMessageContaining("down");
        assertThat(lookup.find(userId).block()).isEmpty();
        assertThat(calls).hasValue(2);
    }
}
