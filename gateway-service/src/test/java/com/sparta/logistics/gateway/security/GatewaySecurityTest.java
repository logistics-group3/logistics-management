package com.sparta.logistics.gateway.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.web.reactive.function.server.RequestPredicates.path;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 실제 Security 체인과 라우팅을 태운다. 모든 /api/** 요청은 테스트 echo 핸들러로 전달되고, echo는 받은 X-User-* 헤더를 돌려준다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.config.enabled=false",
                "eureka.client.enabled=false",
                "jwt.secret=" + GatewaySecurityTest.SECRET,
                "auth.whitelist[0]=/api/v1/auth/signup",
                "auth.whitelist[1]=/api/v1/auth/login",
                "auth.user-cache-ttl=60s",
                "spring.cloud.gateway.server.webflux.routes[0].id=echo",
                "spring.cloud.gateway.server.webflux.routes[0].uri=forward:/echo",
                "spring.cloud.gateway.server.webflux.routes[0].predicates[0]=Path=/api/**"
        })
class GatewaySecurityTest {

    static final String SECRET = "test-only-jwt-secret-key-0123456789abcdef";
    private static final UUID USER_ID = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
    private static final UserSnapshot STORED_USER = new UserSnapshot(USER_ID, "hong01", "USER", false);

    @Autowired
    private WebTestClient client;

    @MockitoBean
    private UserLookup userLookup;

    @TestConfiguration
    static class EchoConfig {

        @Bean
        RouterFunction<ServerResponse> echo() {
            return RouterFunctions.route(path("/echo"), request -> {
                Map<String, String> headers = new LinkedHashMap<>();
                UserHeaders.ALL.forEach(name -> headers.put(name, request.headers().firstHeader(name)));
                return ServerResponse.ok().bodyValue(headers);
            });
        }
    }

    private static String token(String secret, String role, Instant expiresAt) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(USER_ID.toString())
                .claim("username", "hong01")
                .claim("role", role)
                .issueTime(Date.from(expiresAt.minusSeconds(3600)))
                .expirationTime(Date.from(expiresAt))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    private static String validToken(String role) throws Exception {
        return token(SECRET, role, Instant.now().plusSeconds(3600));
    }

    @Test
    @DisplayName("유효한 토큰이면 클라이언트가 보낸 X-User-Role을 지우고 저장된 값으로 다시 넣는다")
    void injectsVerifiedHeaders() throws Exception {
        given(userLookup.find(USER_ID)).willReturn(Mono.just(Optional.of(STORED_USER)));

        client.get().uri("/api/v1/users/sample/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken("USER"))
                .header(UserHeaders.ROLE, "MASTER")
                .header(UserHeaders.USERNAME, "evil")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$['X-User-Id']").isEqualTo(USER_ID.toString())
                .jsonPath("$['X-Username']").isEqualTo("hong01")
                .jsonPath("$['X-User-Role']").isEqualTo("USER")
                .jsonPath("$['X-Delivery-Manager']").isEqualTo("false");
    }

    @Test
    @DisplayName("토큰이 없으면 401 UNAUTHORIZED (SA 6.1 형식)")
    void missingToken() {
        client.get().uri("/api/v1/hubs")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.path").isEqualTo("/api/v1/hubs")
                .jsonPath("$.timestamp").exists();
    }

    @Test
    @DisplayName("다른 키로 서명했거나 만료된 토큰은 401 UNAUTHORIZED이고 user-service를 부르지 않는다")
    void forgedOrExpiredToken() throws Exception {
        String forged = token("attacker-secret-key-0123456789abcdefghij", "MASTER", Instant.now().plusSeconds(3600));
        String expired = token(SECRET, "USER", Instant.now().minusSeconds(60));

        for (String token : new String[] {forged, expired}) {
            client.get().uri("/api/v1/hubs")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody().jsonPath("$.code").isEqualTo("UNAUTHORIZED");
        }
        verify(userLookup, never()).find(any());
    }

    @Test
    @DisplayName("토큰의 role이 저장된 role과 다르면 401 TOKEN_ROLE_MISMATCH")
    void roleMismatch() throws Exception {
        given(userLookup.find(USER_ID)).willReturn(Mono.just(Optional.of(STORED_USER)));

        client.get().uri("/api/v1/hubs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken("MASTER"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("TOKEN_ROLE_MISMATCH");
    }

    @Test
    @DisplayName("탈퇴한 사용자(I-01 404)는 401 TOKEN_ROLE_MISMATCH")
    void withdrawnUser() throws Exception {
        given(userLookup.find(USER_ID)).willReturn(Mono.just(Optional.empty()));

        client.get().uri("/api/v1/hubs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken("USER"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("TOKEN_ROLE_MISMATCH");
    }

    @Test
    @DisplayName("user-service 조회가 실패하면 503 SERVICE_UNAVAILABLE")
    void lookupFailure() throws Exception {
        given(userLookup.find(USER_ID)).willReturn(Mono.error(new IllegalStateException("down")));

        client.get().uri("/api/v1/hubs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken("USER"))
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test
    @DisplayName("/internal/** 은 토큰이 있어도 404")
    void blocksInternal() throws Exception {
        client.get().uri("/internal/users/" + USER_ID)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken("USER"))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("로그인은 만료 토큰이 붙어 있어도 인증 없이 통과하고, 위조 헤더는 지운다")
    void whitelistBypassesSecurity() throws Exception {
        client.post().uri("/api/v1/auth/login")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(SECRET, "USER", Instant.now().minusSeconds(60)))
                .header(UserHeaders.ROLE, "MASTER")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$['X-User-Role']").doesNotExist();
    }
}
