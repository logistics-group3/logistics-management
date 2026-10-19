package com.sparta.logistics.user.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sparta.logistics.common.auth.UserAuthCacheKeys;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class RedisUserAuthCacheTest {

    @Mock
    private StringRedisTemplate redis;
    @InjectMocks
    private RedisUserAuthCache cache;

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("Gateway와 같은 키 규칙(auth:user:{userId})으로 지운다")
    void deletesGatewayKey() {
        cache.evict(userId);

        verify(redis).delete(UserAuthCacheKeys.of(userId));
    }

    @Test
    @DisplayName("Redis 장애로 삭제가 실패해도 예외를 밖으로 던지지 않는다(이미 커밋한 변경을 지킨다)")
    void swallowsRedisFailure() {
        given(redis.delete(UserAuthCacheKeys.of(userId))).willThrow(new RedisConnectionFailureException("down"));

        assertThatCode(() -> cache.evict(userId)).doesNotThrowAnyException();
    }
}
