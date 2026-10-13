package com.sparta.logistics.gateway.auth;

import com.sparta.logistics.common.auth.UserRole;
import com.sparta.logistics.gateway.config.GatewayAuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * JWT 서명·만료 검증 (SA 8.2). 발급은 user-service, 검증은 Gateway.
 * Claims: sub(userId), username, role, iat, exp / HS256
 */
@Component
public class JwtVerifier {

    private final SecretKey key;

    public JwtVerifier(GatewayAuthProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** @throws InvalidTokenException 서명 위조, 만료, 형식 오류, 필수 클레임 누락 */
    public TokenClaims verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return new TokenClaims(
                    UUID.fromString(claims.getSubject()),
                    claims.get("username", String.class),
                    UserRole.valueOf(claims.get("role", String.class))
            );
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            throw new InvalidTokenException(e);
        }
    }

    public record TokenClaims(UUID userId, String username, UserRole role) {
    }

    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(Throwable cause) {
            super(cause);
        }
    }
}
