package com.sparta.logistics.gateway.filter;

import com.sparta.logistics.gateway.client.UserLookupClient;
import com.sparta.logistics.gateway.client.UserLookupClient.InternalUser;
import com.sparta.logistics.gateway.client.UserLookupClient.Status;
import com.sparta.logistics.gateway.config.GatewayAuthProperties;
import com.sparta.logistics.gateway.filter.JwtVerifier.TokenClaims;
import com.sparta.logistics.gateway.support.GatewayErrorWriter;
import com.sparta.logistics.gateway.support.GatewayErrorWriter.GatewayError;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
 * Gateway 인증 필터 (SA 8.3 처리 순서)
 * <ol>
 *   <li>클라이언트가 보낸 X-User-* 헤더 제거 (헤더 위조 방지)</li>
 *   <li>{@code /internal/**} 요청은 404 (외부 차단, 서비스 간 Feign 전용)</li>
 *   <li>화이트리스트(회원가입·로그인·Swagger)는 인증 없이 통과</li>
 *   <li>Authorization 헤더의 JWT 서명·만료 검증 → 실패 시 401 UNAUTHORIZED</li>
 *   <li>user-service I-01로 role·탈퇴 여부 재확인 (60초 캐시) → 불일치·탈퇴 시 401 TOKEN_ROLE_MISMATCH</li>
 *   <li>X-User-Id, X-Username, X-User-Role, X-Delivery-Manager 주입 후 라우팅</li>
 * </ol>
 * 역할별 접근 제어(MASTER 전용 등)는 각 서비스의 {@code @RequireRole}이, 소유자 검증은 Application 계층이 담당한다.
 */
@Component
@RequiredArgsConstructor
public class AuthenticationFilter implements GlobalFilter, Ordered {

    static final String USER_ID = "X-User-Id";
    static final String USERNAME = "X-Username";
    static final String USER_ROLE = "X-User-Role";
    static final String DELIVERY_MANAGER = "X-Delivery-Manager";
    static final List<String> USER_HEADERS = List.of(USER_ID, USERNAME, USER_ROLE, DELIVERY_MANAGER);

    private static final String INTERNAL_PATH = "/internal/**";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final JwtVerifier jwtVerifier;
    private final UserLookupClient userLookupClient;
    private final GatewayAuthProperties properties;
    private final GatewayErrorWriter errorWriter;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // 1. 사용자 헤더 제거
        ServerHttpRequest stripped = exchange.getRequest().mutate()
                .headers(headers -> USER_HEADERS.forEach(headers::remove))
                .build();

        // 2. 내부 API 차단
        if (MATCHER.match(INTERNAL_PATH, path)) {
            return errorWriter.write(exchange, GatewayError.RESOURCE_NOT_FOUND);
        }

        // 3. 화이트리스트
        if (isPublic(path)) {
            return chain.filter(exchange.mutate().request(stripped).build());
        }

        // 4. JWT 검증
        String authorization = stripped.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return errorWriter.write(exchange, GatewayError.UNAUTHORIZED);
        }
        TokenClaims claims = jwtVerifier.verify(authorization.substring(BEARER_PREFIX.length())).orElse(null);
        if (claims == null) {
            return errorWriter.write(exchange, GatewayError.UNAUTHORIZED);
        }

        // TODO: 10만명 이상 부터 레디스 사용 고려
        // 5. 권한 재확인 → 6. 헤더 주입
        return userLookupClient.lookup(claims.userId()).flatMap(lookup -> {
            if (lookup.status() == Status.UNAVAILABLE) {
                return errorWriter.write(exchange, GatewayError.SERVICE_UNAVAILABLE);
            }
            if (lookup.status() == Status.NOT_FOUND || !lookup.user().role().equals(claims.role())) {
                return errorWriter.write(exchange, GatewayError.TOKEN_ROLE_MISMATCH);
            }
            InternalUser user = lookup.user();
            ServerHttpRequest authenticated = stripped.mutate()
                    .header(USER_ID, user.userId().toString())
                    .header(USERNAME, user.username())
                    .header(USER_ROLE, user.role())
                    .header(DELIVERY_MANAGER, String.valueOf(user.deliveryManager()))
                    .build();
            return chain.filter(exchange.mutate().request(authenticated).build());
        });
    }

    private boolean isPublic(String path) {
        return properties.getPublicPaths().stream().anyMatch(pattern -> MATCHER.match(pattern, path));
    }

    /** 라우팅 필터보다 먼저 실행 */
    @Override
    public int getOrder() {
        return -1;
    }
}
