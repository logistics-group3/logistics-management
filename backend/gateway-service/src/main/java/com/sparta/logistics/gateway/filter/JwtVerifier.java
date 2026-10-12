package com.sparta.logistics.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * user-service가 발급한 JWT(HS256)의 서명·만료를 검증한다 (SA 8.2).
 * Claims: sub(userId), username, role, iat, exp
 */
@Component
public class JwtVerifier {

    private final SecretKey key;

    public JwtVerifier(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Optional<TokenClaims> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new TokenClaims(
                    UUID.fromString(claims.getSubject()),
                    claims.get("username", String.class),
                    claims.get("role", String.class)
            ));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record TokenClaims(UUID userId, String username, String role) {
    }
}
