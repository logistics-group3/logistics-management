package com.sparta.logistics.gateway.auth;

import com.sparta.logistics.gateway.support.ErrorResponseWriter;
import com.sparta.logistics.gateway.support.GatewayErrorCode;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * SA 8.3의 Gateway 필터 처리 순서를 그대로 구현한다. 메서드 안의 번호가 SA 단계 번호다.
 *
 * Note: GlobalFilter가 아니라 WebFilter인 이유. GlobalFilter는 라우트가 매칭된 요청에만 실행되므로, 라우트가 없는
 * /internal/** 은 필터를 거치지 않고 기본 404가 나간다. WebFilter는 라우트 매칭 전에 모든 요청에 실행되어
 * 차단과 에러 형식을 한곳에서 보장한다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthenticationFilter implements WebFilter {

    private static final String BEARER = "Bearer ";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final JwtVerifier jwtVerifier;
    private final UserLookup userLookup;
    private final AuthProperties properties;
    private final ErrorResponseWriter errorWriter;

    public AuthenticationFilter(JwtVerifier jwtVerifier, UserLookup userLookup, AuthProperties properties,
                                ErrorResponseWriter errorWriter) {
        this.jwtVerifier = jwtVerifier;
        this.userLookup = userLookup;
        this.properties = properties;
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 1. 클라이언트가 보낸 X-User-* 헤더 제거
        // Note: 서비스는 이 헤더를 검증 없이 믿는다. 위조 헤더를 여기서 지우지 않으면 누구나 X-User-Role: MASTER로 관리자가 된다.
        ServerHttpRequest stripped = exchange.getRequest().mutate()
                .headers(headers -> UserHeaders.ALL.forEach(headers::remove))
                .build();
        ServerWebExchange sanitized = exchange.mutate().request(stripped).build();
        String path = stripped.getPath().value();

        // 2. /internal/** 는 외부에서 호출할 수 없다
        if (PATH_MATCHER.match("/internal/**", path) || PATH_MATCHER.match("/internal", path)) {
            return errorWriter.write(sanitized, GatewayErrorCode.NOT_FOUND);
        }

        // 3. 화이트리스트는 인증 없이 통과 (헤더는 이미 지운 상태)
        if (properties.whitelist().stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path))) {
            return chain.filter(sanitized);
        }

        // 4. 토큰 서명·만료 검증
        Optional<TokenClaims> claims = extractToken(stripped).flatMap(jwtVerifier::verify);
        if (claims.isEmpty()) {
            return errorWriter.write(sanitized, GatewayErrorCode.UNAUTHORIZED);
        }
        TokenClaims token = claims.get();

        // 5. 저장된 role과 탈퇴 여부 재확인 (I-01, 60초 캐시)
        return userLookup.find(token.userId())
                .map(found -> found.filter(user -> user.role().equals(token.role()))
                        .map(Verification::allow)
                        .orElseGet(() -> Verification.reject(GatewayErrorCode.TOKEN_ROLE_MISMATCH)))
                .onErrorResume(e -> {
                    log.warn("user-service 권한 재확인 실패: userId={}, cause={}", token.userId(), e.toString());
                    return Mono.just(Verification.reject(GatewayErrorCode.SERVICE_UNAVAILABLE));
                })
                // 6. 검증된 값으로 X-User-* 헤더를 다시 넣고 라우팅
                .flatMap(result -> result.user() != null
                        ? chain.filter(withUserHeaders(sanitized, result.user()))
                        : errorWriter.write(sanitized, result.error()));
    }

    private Optional<String> extractToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            return Optional.empty();
        }
        return Optional.of(header.substring(BEARER.length()).trim());
    }

    private ServerWebExchange withUserHeaders(ServerWebExchange exchange, UserSnapshot user) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(UserHeaders.USER_ID, user.userId().toString())
                .header(UserHeaders.USERNAME, user.username())
                .header(UserHeaders.ROLE, user.role())
                .header(UserHeaders.DELIVERY_MANAGER, String.valueOf(user.isDeliveryManager()))
                .build();
        return exchange.mutate().request(request).build();
    }

    private record Verification(UserSnapshot user, GatewayErrorCode error) {

        static Verification allow(UserSnapshot user) {
            return new Verification(user, null);
        }

        static Verification reject(GatewayErrorCode error) {
            return new Verification(null, error);
        }
    }
}
