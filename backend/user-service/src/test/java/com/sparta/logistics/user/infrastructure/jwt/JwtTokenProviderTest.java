package com.sparta.logistics.user.infrastructure.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.sparta.logistics.common.security.UserRole;
import com.sparta.logistics.user.infrastructure.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-32-bytes!!";
    private final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    @Test
    @DisplayName("유효시간 설정이 바뀌면 같은 Bean에서 다음 발급부터 새 유효시간이 적용된다 (refresh 재바인딩)")
    void expiration_followsProperties() {
        // given
        JwtProperties properties = new JwtProperties();
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, properties);

        // when: 기본 60분 → refresh로 1분이 재바인딩된 상황
        String before = provider.createAccessToken(UUID.randomUUID(), "hong01", UserRole.USER);
        long beforeSeconds = provider.getExpirationSeconds();
        properties.setExpirationMinutes(1);
        String after = provider.createAccessToken(UUID.randomUUID(), "hong01", UserRole.USER);

        // then
        assertThat(beforeSeconds).isEqualTo(3600);
        assertThat(lifetimeSeconds(before)).isEqualTo(3600);
        assertThat(provider.getExpirationSeconds()).isEqualTo(60);
        assertThat(lifetimeSeconds(after)).isEqualTo(60);
    }

    private long lifetimeSeconds(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
    }
}
