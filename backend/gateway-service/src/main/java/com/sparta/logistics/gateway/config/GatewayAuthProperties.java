package com.sparta.logistics.gateway.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * gateway.auth.* (application.yml)
 *
 * @param publicPaths       인증 없이 통과하는 경로 (Ant 패턴)
 * @param roleCacheTtl      권한 재확인 결과 캐시 시간 (Notion 공통 인프라 범위: 60초)
 * @param userLookupTimeout user-service 내부 API 호출 타임아웃
 */
@ConfigurationProperties(prefix = "gateway.auth")
public record GatewayAuthProperties(
        List<String> publicPaths,
        Duration roleCacheTtl,
        Duration userLookupTimeout
) {

    public GatewayAuthProperties {
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
        roleCacheTtl = roleCacheTtl == null ? Duration.ofSeconds(60) : roleCacheTtl;
        userLookupTimeout = userLookupTimeout == null ? Duration.ofSeconds(3) : userLookupTimeout;
    }
}
