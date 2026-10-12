package com.sparta.logistics.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.sparta.logistics.gateway.filter.JwtVerifier.TokenClaims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtVerifierTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-32-bytes!!";
    private final JwtVerifier verifier = new JwtVerifier(SECRET);

    @Test
    @DisplayName("user-service와 같은 형식의 토큰에서 userId·username·role을 꺼낸다")
    void verifyValidToken() {
        // given
        UUID userId = UUID.randomUUID();
        String token = token(SECRET, userId, Instant.now().plusSeconds(60));

        // when
        Optional<TokenClaims> claims = verifier.verify(token);

        // then
        assertThat(claims).isPresent();
        assertThat(claims.get().userId()).isEqualTo(userId);
        assertThat(claims.get().username()).isEqualTo("hong01");
        assertThat(claims.get().role()).isEqualTo("USER");
    }

    @Test
    @DisplayName("만료된 토큰은 거부한다")
    void rejectExpired() {
        String token = token(SECRET, UUID.randomUUID(), Instant.now().minusSeconds(1));

        assertThat(verifier.verify(token)).isEmpty();
    }

    @Test
    @DisplayName("다른 키로 서명한 토큰은 거부한다")
    void rejectWrongSignature() {
        String token = token("another-secret-key-must-be-at-least-32-bytes!", UUID.randomUUID(),
                Instant.now().plusSeconds(60));

        assertThat(verifier.verify(token)).isEmpty();
    }

    private String token(String secret, UUID userId, Instant expiresAt) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", "hong01")
                .claim("role", "USER")
                .issuedAt(Date.from(expiresAt.minusSeconds(3600)))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
