package com.sparta.logistics.user.infrastructure.jwt;

import com.sparta.logistics.common.security.UserRole;
import com.sparta.logistics.user.application.TokenProvider;
import com.sparta.logistics.user.infrastructure.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT 발급 (SA 8.2): HS256, 유효시간 60분, Claims = sub(userId), username, role, iat, exp.
 * <ul>
 *   <li>secret: Gateway와 같은 JWT_SECRET 환경 변수 (32바이트 이상). 기동 시 한 번 키를 만든다 → 교체는 재시작</li>
 *   <li>유효시간: {@link JwtProperties}에서 발급할 때마다 읽는다 → config/user-service.yml 변경 시 재시작 없이 반영</li>
 * </ul>
 */
@Component
public class JwtTokenProvider implements TokenProvider {

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret, JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.properties = properties;
    }

    @Override
    public String createAccessToken(UUID userId, String username, UserRole role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration())))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    @Override
    public long getExpirationSeconds() {
        return expiration().toSeconds();
    }

    private Duration expiration() {
        return Duration.ofMinutes(properties.getExpirationMinutes());
    }
}
