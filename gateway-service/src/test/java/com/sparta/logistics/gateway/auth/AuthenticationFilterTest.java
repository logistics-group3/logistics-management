package com.sparta.logistics.gateway.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.gateway.support.ErrorResponseWriter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

class AuthenticationFilterTest {

    private static final String SECRET = "test-only-jwt-secret-key-0123456789abcdef";
    private static final UUID USER_ID = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
    private static final UserSnapshot STORED_USER = new UserSnapshot(USER_ID, "hong01", "USER", false);
    private static final AuthProperties PROPERTIES = new AuthProperties(
            List.of("/api/v1/auth/signup", "/api/v1/auth/login"), Duration.ofSeconds(60));

    private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    private final WebFilterChain chain = exchange -> {
        forwarded.set(exchange);
        return Mono.empty();
    };

    private AuthenticationFilter filter(UserLookup lookup) {
        return new AuthenticationFilter(new JwtVerifier(new JwtProperties(SECRET)), lookup, PROPERTIES,
                new ErrorResponseWriter(new ObjectMapper().findAndRegisterModules()));
    }

    private static String token(String secret, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(USER_ID.toString())
                .claim("username", "hong01")
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private MockServerWebExchange run(UserLookup lookup, MockServerHttpRequest request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        filter(lookup).filter(exchange, chain).block();
        return exchange;
    }

    private static String body(MockServerWebExchange exchange) {
        return exchange.getResponse().getBodyAsString().block();
    }

    @Test
    @DisplayName("유효한 토큰이면 클라이언트가 보낸 X-User-Role을 지우고 저장된 값으로 다시 넣는다")
    void replacesForgedHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/sample/admin")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(SECRET, "USER"))
                .header(UserHeaders.ROLE, "MASTER")
                .build();

        run(id -> Mono.just(Optional.of(STORED_USER)), request);

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.get(UserHeaders.ROLE)).containsExactly("USER");
        assertThat(headers.getFirst(UserHeaders.USER_ID)).isEqualTo(USER_ID.toString());
        assertThat(headers.getFirst(UserHeaders.USERNAME)).isEqualTo("hong01");
        assertThat(headers.getFirst(UserHeaders.DELIVERY_MANAGER)).isEqualTo("false");
    }

    @Test
    @DisplayName("토큰이 없으면 401 UNAUTHORIZED")
    void missingToken() {
        MockServerWebExchange exchange = run(id -> Mono.never(), MockServerHttpRequest.get("/api/v1/hubs").build());

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(body(exchange)).contains("\"code\":\"UNAUTHORIZED\"").contains("\"path\":\"/api/v1/hubs\"");
        assertThat(forwarded.get()).isNull();
    }

    @Test
    @DisplayName("다른 키로 서명한 위조 토큰은 401 UNAUTHORIZED")
    void forgedToken() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/hubs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("attacker-secret-key-0123456789abcdefgh", "MASTER"))
                .build();

        MockServerWebExchange exchange = run(id -> Mono.just(Optional.of(STORED_USER)), request);

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(body(exchange)).contains("\"code\":\"UNAUTHORIZED\"");
    }

    @Test
    @DisplayName("토큰의 role이 저장된 role과 다르거나 탈퇴했으면 401 TOKEN_ROLE_MISMATCH")
    void roleMismatchOrWithdrawn() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/hubs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(SECRET, "MASTER"))
                .build();

        MockServerWebExchange mismatch = run(id -> Mono.just(Optional.of(STORED_USER)), request);
        MockServerWebExchange withdrawn = run(id -> Mono.just(Optional.empty()), request);

        assertThat(body(mismatch)).contains("\"code\":\"TOKEN_ROLE_MISMATCH\"");
        assertThat(body(withdrawn)).contains("\"code\":\"TOKEN_ROLE_MISMATCH\"");
        assertThat(forwarded.get()).isNull();
    }

    @Test
    @DisplayName("/internal/** 은 토큰이 있어도 404로 막는다")
    void blocksInternal() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/internal/users/" + USER_ID)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(SECRET, "USER"))
                .build();

        MockServerWebExchange exchange = run(id -> Mono.just(Optional.of(STORED_USER)), request);

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(forwarded.get()).isNull();
    }

    @Test
    @DisplayName("로그인·회원가입은 토큰 없이 통과하되 위조 헤더는 지운다")
    void whitelistPassesWithoutHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/auth/login")
                .header(UserHeaders.ROLE, "MASTER")
                .build();

        run(id -> Mono.never(), request);

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().containsKey(UserHeaders.ROLE)).isFalse();
    }

    @Test
    @DisplayName("user-service 조회가 실패하면 503 SERVICE_UNAVAILABLE")
    void lookupFailure() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/hubs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(SECRET, "USER"))
                .build();

        MockServerWebExchange exchange = run(id -> Mono.error(new IllegalStateException("down")), request);

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(body(exchange)).contains("\"code\":\"SERVICE_UNAVAILABLE\"");
    }
}
