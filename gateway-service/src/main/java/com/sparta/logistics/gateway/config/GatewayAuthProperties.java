package com.sparta.logistics.gateway.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 gateway.auth.*
 *
 * @param jwtSecret     user-service 와 같은 값 (환경 변수 JWT_SECRET, 32바이트 이상)
 * @param whitelist     인증 없이 통과시키는 경로 (PathPattern 형식)
 * @param roleCacheTtl  권한 재확인 결과 캐시 시간 (SA 8.2: 60초)
 */
@ConfigurationProperties(prefix = "gateway.auth")
public record GatewayAuthProperties(
        String jwtSecret,
        List<String> whitelist,
        Duration roleCacheTtl
) {
}
