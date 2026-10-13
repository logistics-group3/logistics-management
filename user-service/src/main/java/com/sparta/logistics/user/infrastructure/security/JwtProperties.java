package com.sparta.logistics.user.infrastructure.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param secret     Gateway 와 같은 값 (환경 변수 JWT_SECRET, 32바이트 이상)
 * @param expiration 토큰 유효시간 (SA 8.2: 60분)
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, Duration expiration) {
}
