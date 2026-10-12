package com.sparta.logistics.user.infrastructure.jwt;

import com.sparta.logistics.common.security.UserRole;
import com.sparta.logistics.user.application.TokenProvider;
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
 * secret은 Gateway와 같은 JWT_SECRET 환경 변수 (32바이트 이상).
 */
@Component
public class JwtTokenProvider implements TokenProvider {

    private final SecretKey key;
    private final Duration expiration;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret,
                            @Value("${jwt.expiration-minutes:60}") long expirationMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = Duration.ofMinutes(expirationMinutes);
    }

    @Override
    public String createAccessToken(UUID userId, String username, UserRole role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    @Override
    public long getExpirationSeconds() {
        return expiration.toSeconds();
    }
}
