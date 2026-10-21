package com.sparta.logistics.user.infrastructure.cache;

import com.sparta.logistics.user.application.UserAuthCache;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * redis일 때만 Redis 키를 지운다. local 캐시는 Gateway 메모리에 있어 user-service가 지울 수 없고 TTL로만 반영된다.
 */
@Configuration
@EnableConfigurationProperties(UserAuthCacheProperties.class)
public class UserAuthCacheConfig {

    @Bean
    public UserAuthCache userAuthCache(UserAuthCacheProperties properties, ObjectProvider<StringRedisTemplate> redis) {
        if (properties.redis()) {
            return new RedisUserAuthCache(redis.getObject());
        }
        return userId -> { };
    }
}
