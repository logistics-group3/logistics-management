package com.sparta.logistics.gateway.filter;

import com.sparta.logistics.common.auth.UserHeaders;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.common.exception.ErrorCode;
import com.sparta.logistics.gateway.auth.JwtVerifier;
import com.sparta.logistics.gateway.auth.JwtVerifier.InvalidTokenException;
import com.sparta.logistics.gateway.auth.JwtVerifier.TokenClaims;
import com.sparta.logistics.gateway.auth.UserAuthClient;
import com.sparta.logistics.gateway.auth.UserAuthClient.UserNotFoundException;
import com.sparta.logistics.gateway.auth.UserAuthInfo;
import com.sparta.logistics.gateway.config.GatewayAuthProperties;
import com.sparta.logistics.gateway.support.ErrorResponseWriter;
import java.util.List;
import java.util.concurrent.CompletionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

/**
 * 인증 필터 (SA 8.3 Gateway 필터 처리 순서)
 *  1. 클라이언트가 보낸 X-User-* 헤더 제거 (헤더 위조 방지) — 화이트리스트 경로 포함 모든 요청
 *  2. (/internal/** 차단은 InternalPathBlockFilter 에서 먼저 처리)
 *  3. 화이트리스트(회원가입·로그인·Swagger)는 인증 없이 통과
 *  4. Authorization: Bearer 토큰 서명·만료 검증 → 실패 시 401 UNAUTHORIZED
 *  5. user-service I-01 로 role·탈퇴 여부 재확인 (60초 캐시) → 불일치 시 401 TOKEN_ROLE_MISMATCH
 *  6. X-User-Id, X-Username, X-User-Role, X-Delivery-Manager 주입 후 라우팅
 */
@Slf4j
@Component
public class AuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtVerifier jwtVerifier;
    private final UserAuthClient userAuthClient;
    private final ErrorResponseWriter errorResponseWriter;
    private final List<PathPattern> whitelist;

    public AuthenticationGlobalFilter(JwtVerifier jwtVerifier,
                                      UserAuthClient userAuthClient,
                                      ErrorResponseWriter errorResponseWriter,
                                      GatewayAuthProperties properties) {
        this.jwtVerifier = jwtVerifier;
        this.userAuthClient = userAuthClient;
        this.errorResponseWriter = errorResponseWriter;
        this.whitelist = properties.whitelist().stream()
                .map(PathPatternParser.defaultInstance::parse)
                .toList();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. 위조 헤더 제거
        ServerHttpRequest sanitized = exchange.getRequest().mutate()
                .headers(headers -> UserHeaders.ALL.forEach(headers::remove))
                .build();
        ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitized).build();

        // 3. 화이트리스트
        if (isWhitelisted(sanitized)) {
            return chain.filter(sanitizedExchange);
        }

        // 4. 토큰 검증
        String token = resolveToken(sanitized);
        if (token == null) {
            return errorResponseWriter.write(exchange, CommonErrorCode.UNAUTHORIZED);
        }
        TokenClaims claims;
        try {
            claims = jwtVerifier.verify(token);
        } catch (InvalidTokenException e) {
            log.debug("Invalid token: {}", e.getMessage());
            return errorResponseWriter.write(exchange, CommonErrorCode.UNAUTHORIZED);
        }

        // 5. 권한 재확인 — 에러 처리는 user-service 조회에만 건다 (라우팅 중 에러는 Gateway 기본 처리에 맡김)
        return userAuthClient.getUser(claims.userId())
                .map(user -> user.role() == claims.role()
                        ? Verification.success(user)
                        : Verification.fail(CommonErrorCode.TOKEN_ROLE_MISMATCH))
                .onErrorResume(e -> Mono.just(Verification.fail(toErrorCode(e, claims))))
                // 6. 헤더 주입 후 라우팅
                .flatMap(result -> result.user() != null
                        ? chain.filter(withUserHeaders(sanitizedExchange, result.user()))
                        : errorResponseWriter.write(exchange, result.error()));
    }

    private ErrorCode toErrorCode(Throwable e, TokenClaims claims) {
        Throwable cause = (e instanceof CompletionException && e.getCause() != null) ? e.getCause() : e;
        if (cause instanceof UserNotFoundException) {
            // 탈퇴(논리 삭제)했거나 존재하지 않는 사용자
            return CommonErrorCode.TOKEN_ROLE_MISMATCH;
        }
        log.error("Role re-check failed. userId={}", claims.userId(), cause);
        return CommonErrorCode.SERVICE_UNAVAILABLE;
    }

    private record Verification(UserAuthInfo user, ErrorCode error) {
        static Verification success(UserAuthInfo user) {
            return new Verification(user, null);
        }

        static Verification fail(ErrorCode error) {
            return new Verification(null, error);
        }
    }

    private ServerWebExchange withUserHeaders(ServerWebExchange exchange, UserAuthInfo user) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.set(UserHeaders.USER_ID, user.userId().toString());
                    headers.set(UserHeaders.USERNAME, user.username());
                    headers.set(UserHeaders.USER_ROLE, user.role().name());
                    headers.set(UserHeaders.DELIVERY_MANAGER, String.valueOf(user.isDeliveryManager()));
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    private boolean isWhitelisted(ServerHttpRequest request) {
        PathContainer path = request.getPath().pathWithinApplication();
        return whitelist.stream().anyMatch(pattern -> pattern.matches(path));
    }

    private String resolveToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
