package com.sparta.logistics.hub.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sparta.logistics.hub.application.HubCacheNames;
import com.sparta.logistics.hub.application.dto.HubResult;
import com.sparta.logistics.hub.application.dto.HubRouteResult;
import com.sparta.logistics.hub.application.dto.InternalHubResult;
import com.sparta.logistics.hub.application.dto.InternalHubRouteResult;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

/**
 * 허브·경로 단건 조회 캐시(성능 측정 항목 B). hub.cache.type으로 저장소를 고른다.
 * Note: CacheManager를 직접 만들면 Boot의 캐시 자동 설정이 물러난다. 그래서 설정이 없는 테스트에서도 기본값(none)이 적용되고,
 * 클래스패스에 Redis가 있다는 이유만으로 Redis 캐시가 켜지지 않는다.
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(HubCacheProperties.class)
public class HubCacheConfig {

    /** 캐시마다 값 타입. Redis에는 타입 정보 없이 JSON으로 저장하고 이 타입으로 되읽는다. */
    private static final Map<String, Class<?>> VALUE_TYPES = Map.of(
            HubCacheNames.HUB, HubResult.class,
            HubCacheNames.INTERNAL_HUB, InternalHubResult.class,
            HubCacheNames.HUB_ROUTE, HubRouteResult.class,
            HubCacheNames.INTERNAL_HUB_ROUTE, InternalHubRouteResult.class);

    @Bean
    public CacheManager cacheManager(HubCacheProperties properties, ObjectProvider<RedisConnectionFactory> redis,
                                     ObjectMapper objectMapper) {
        return switch (properties.type()) {
            case NONE -> new NoOpCacheManager();
            case LOCAL -> {
                CaffeineCacheManager manager = new CaffeineCacheManager(VALUE_TYPES.keySet().toArray(String[]::new));
                manager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(properties.ttl()).maximumSize(10_000));
                yield manager;
            }
            case REDIS -> {
                RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(properties.ttl())
                        .disableCachingNullValues()
                        .prefixCacheNameWith("hub:");
                // 이름이 정해진 캐시만 쓴다. 정해지지 않은 이름은 직렬화기가 없어 만들지 않는다.
                RedisCacheManager.RedisCacheManagerBuilder builder = RedisCacheManager.builder(redis.getObject())
                        .disableCreateOnMissingCache();
                VALUE_TYPES.forEach((name, type) -> builder.withCacheConfiguration(name,
                        base.serializeValuesWith(SerializationPair.fromSerializer(new Jackson2JsonRedisSerializer<>(objectMapper, type)))));
                yield builder.build();
            }
        };
    }
}
