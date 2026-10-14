package com.sparta.logistics.user.infrastructure.security;

import com.sparta.logistics.user.application.TokenIssuer;
import com.sparta.logistics.user.domain.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT 계약 (gateway JwtVerifier와 동일해야 함):
 * HS256, sub = userId, claim username·role, iat, exp.
 */
@Component
public class JwtTokenIssuer implements TokenIssuer {

    private final SecretKey key;
    private final long expirationMs;

    public JwtTokenIssuer(@Value("${jwt.secret}") String base64Secret,
                          @Value("${jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.expirationMs = expirationMs;
    }

    @Override
    public String issue(User user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    @Override
    public long expiresInSeconds() {
        return expirationMs / 1000;
    }
}
