package com.sparta.logistics.company.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sparta.logistics.company.application.CompanyCacheNames;
import com.sparta.logistics.company.application.dto.InternalCompanyResult;
import com.sparta.logistics.company.application.dto.InternalProductResult;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

/**
 * order가 주문 생성 때 부르는 업체·상품 내부 조회(I-07, I-08) 캐시(성능 측정 항목 C). company.cache.type으로 저장소를 고른다.
 * Note: 트랜잭션 인식 프록시로 감싼다. 상품 수정·삭제 트랜잭션 안에서 키를 지우면, 커밋 전에 다른 요청이 옛 값을 다시 넣을 수 있다.
 * 프록시는 트랜잭션이 있으면 저장·삭제를 커밋 뒤로 미룬다. CacheManager를 직접 만들어 Boot의 캐시 자동 설정은 물러난다.
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(CompanyCacheProperties.class)
public class CompanyCacheConfig {

    private static final Map<String, Class<?>> VALUE_TYPES = Map.of(
            CompanyCacheNames.INTERNAL_COMPANY, InternalCompanyResult.class,
            CompanyCacheNames.INTERNAL_PRODUCT, InternalProductResult.class);

    @Bean
    public CacheManager cacheManager(CompanyCacheProperties properties, ObjectProvider<RedisConnectionFactory> redis,
                                     ObjectMapper objectMapper) {
        CacheManager manager = switch (properties.type()) {
            case NONE -> new NoOpCacheManager();
            case LOCAL -> {
                CaffeineCacheManager caffeine = new CaffeineCacheManager(VALUE_TYPES.keySet().toArray(String[]::new));
                caffeine.setCaffeine(Caffeine.newBuilder().expireAfterWrite(properties.ttl()).maximumSize(10_000));
                yield caffeine;
            }
            case REDIS -> {
                RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(properties.ttl())
                        .disableCachingNullValues()
                        .prefixCacheNameWith("company:");
                // 이름이 정해진 캐시만 쓴다. 정해지지 않은 이름은 직렬화기가 없어 만들지 않는다.
                RedisCacheManager.RedisCacheManagerBuilder builder = RedisCacheManager.builder(redis.getObject())
                        .disableCreateOnMissingCache();
                VALUE_TYPES.forEach((name, type) -> builder.withCacheConfiguration(name,
                        base.serializeValuesWith(SerializationPair.fromSerializer(new Jackson2JsonRedisSerializer<>(objectMapper, type)))));
                RedisCacheManager redisManager = builder.build();
                // 빈으로 등록되는 것은 바깥 프록시라 Spring이 초기화를 부르지 않는다. 정해 둔 캐시를 직접 만든다.
                redisManager.afterPropertiesSet();
                yield redisManager;
            }
        };
        return new TransactionAwareCacheManagerProxy(manager);
    }
}
