package com.sparta.logistics.hub.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param type 조회 캐시 저장소. none(기본, 캐시 없음), local(인스턴스 메모리, Caffeine), redis(공유)
 * @param ttl  저장 후 만료 시간. 허브·경로는 쓰기 API가 없어(Flyway 시드) 삭제 없이 TTL로만 갱신된다
 */
@ConfigurationProperties(prefix = "hub.cache")
public record HubCacheProperties(
        @DefaultValue("none") Type type,
        @DefaultValue("10m") Duration ttl
) {

    public enum Type {
        NONE, LOCAL, REDIS
    }
}
