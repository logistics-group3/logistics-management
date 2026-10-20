package com.sparta.logistics.user.infrastructure.cache;

import com.sparta.logistics.common.auth.UserAuthCacheKeys;
import com.sparta.logistics.user.application.UserAuthCache;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Gateway가 Redis에 둔 권한 재확인 결과를 지운다(user.auth-cache=redis).
 * Note: 삭제가 실패해도 이미 커밋한 권한 변경을 되돌리지 않는다. Gateway 캐시 TTL(60초)이 지나면 반영된다.
 */
@Slf4j
@RequiredArgsConstructor
public class RedisUserAuthCache implements UserAuthCache {

    private final StringRedisTemplate redis;

    @Override
    public void evict(UUID userId) {
        try {
            redis.delete(UserAuthCacheKeys.of(userId));
        } catch (RuntimeException e) {
            log.warn("Gateway 권한 캐시 삭제 실패, TTL이 지나면 반영된다: userId={}, reason={}", userId, e.getClass().getSimpleName());
        }
    }
}
