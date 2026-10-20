package com.sparta.logistics.company.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param type 내부 조회 캐시 저장소. none(기본, 캐시 없음), local(인스턴스 메모리, Caffeine), redis(공유)
 * @param ttl  저장 후 만료 시간. 수정·삭제는 키를 지워 바로 반영하고, TTL은 삭제가 빠졌을 때의 안전망이다
 */
@ConfigurationProperties(prefix = "company.cache")
public record CompanyCacheProperties(
        @DefaultValue("none") Type type,
        @DefaultValue("10m") Duration ttl
) {

    public enum Type {
        NONE, LOCAL, REDIS
    }
}
