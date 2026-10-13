package com.sparta.logistics.user.infrastructure.security;

import com.sparta.logistics.user.domain.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * JWT 발급 (SA 8.2) — HS256, Claims: sub(userId), username, role, iat, exp
 * 검증은 Gateway(JwtVerifier) 에서 한다.
 */
@Component
public class JwtProvider {

    private final SecretKey key;
    private final long expirationMillis;

    public JwtProvider(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = properties.expiration().toMillis();
    }

    public String createAccessToken(User user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMillis))
                .signWith(key, Jwts.SIG.HS256)   // 키 길이와 상관없이 HS256 고정
                .compact();
    }

    public long getExpirationSeconds() {
        return expirationMillis / 1000;
    }
}
