package com.sparta.logistics.gateway.auth;

import com.sparta.logistics.gateway.auth.JwtVerifier.TokenClaims;
import com.sparta.logistics.gateway.auth.UserClient.InternalUser;
import com.sparta.logistics.gateway.error.GatewayErrorCode;
import com.sparta.logistics.gateway.error.GatewayErrorWriter;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 모든 외부 요청의 인증 처리.
 * 1) 클라이언트가 보낸 X-User-* 헤더 제거 (위조 방지)
 * 2) /internal/** 차단
 * 3) 공개 경로(회원가입·로그인·문서) 통과
 * 4) JWT 검증 → user-service(I-01)로 사용자 존재·권한 재확인 → X-User-* 헤더 주입
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    private static final List<String> USER_HEADERS =
            List.of("X-User-Id", "X-Username", "X-User-Role", "X-Delivery-Manager");

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            "/docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/webjars/**"
    );

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final JwtVerifier jwtVerifier;
    private final UserClient userClient;
    private final GatewayErrorWriter errorWriter;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        ServerHttpRequest stripped = exchange.getRequest().mutate()
                .headers(headers -> USER_HEADERS.forEach(headers::remove))
                .build();
        ServerWebExchange strippedExchange = exchange.mutate().request(stripped).build();

        if (PATH_MATCHER.match("/internal/**", path)) {
            return errorWriter.write(strippedExchange, GatewayErrorCode.FORBIDDEN);
        }
        if (isPublic(path)) {
            return chain.filter(strippedExchange);
        }

        Optional<TokenClaims> claims = extractToken(stripped).flatMap(jwtVerifier::verify);
        if (claims.isEmpty()) {
            return errorWriter.write(strippedExchange, GatewayErrorCode.UNAUTHORIZED);
        }
        TokenClaims token = claims.get();

        // 사용자 조회 결과만 에러 처리하고, 이후 라우팅(chain.filter)에서 난 에러는 건드리지 않는다.
        return userClient.findUser(token.userId())
                .map(UserLookup::found)
                .defaultIfEmpty(UserLookup.notFound())
                .onErrorResume(e -> {
                    log.warn("user-service 사용자 확인 실패: {}", e.toString());
                    return Mono.just(UserLookup.lookupFailed());
                })
                .flatMap(lookup -> {
                    if (lookup.failed()) {
                        return errorWriter.write(strippedExchange, GatewayErrorCode.SERVICE_UNAVAILABLE);
                    }
                    if (lookup.user() == null) {
                        return errorWriter.write(strippedExchange, GatewayErrorCode.UNAUTHORIZED);
                    }
                    return authenticate(strippedExchange, chain, token, lookup.user());
                });
    }

    private record UserLookup(InternalUser user, boolean failed) {

        static UserLookup found(InternalUser user) {
            return new UserLookup(user, false);
        }

        static UserLookup notFound() {
            return new UserLookup(null, false);
        }

        static UserLookup lookupFailed() {
            return new UserLookup(null, true);
        }
    }

    private Mono<Void> authenticate(ServerWebExchange exchange, GatewayFilterChain chain,
                                    TokenClaims token, InternalUser user) {
        if (!user.role().equals(token.role())) {
            return errorWriter.write(exchange, GatewayErrorCode.TOKEN_ROLE_MISMATCH);
        }
        ServerHttpRequest authenticated = exchange.getRequest().mutate()
                .header("X-User-Id", user.userId().toString())
                .header("X-Username", user.username())
                .header("X-User-Role", user.role())
                .header("X-Delivery-Manager", String.valueOf(user.isDeliveryManager()))
                .build();
        return chain.filter(exchange.mutate().request(authenticated).build());
    }

    private Optional<String> extractToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(authorization.substring(BEARER_PREFIX.length()));
    }

    private boolean isPublic(String path) {
        return PUBLIC_PATHS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
