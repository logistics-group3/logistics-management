package com.sparta.logistics.gateway.security;

import com.sparta.logistics.gateway.support.ErrorResponseWriter;
import com.sparta.logistics.gateway.support.GatewayErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 인증 실패를 SA 6.1 에러 형식으로 응답한다. 토큰 누락·위조·만료는 401 UNAUTHORIZED,
 * role 불일치·탈퇴는 401 TOKEN_ROLE_MISMATCH, user-service 장애는 503.
 */
@Slf4j
public class JsonAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final ErrorResponseWriter errorWriter;

    public JsonAuthenticationEntryPoint(ErrorResponseWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        GatewayErrorCode errorCode = ex instanceof GatewayAuthenticationException gateway
                ? gateway.getErrorCode()
                : GatewayErrorCode.UNAUTHORIZED;
        if (errorCode == GatewayErrorCode.SERVICE_UNAVAILABLE) {
            log.warn("user-service 권한 재확인 실패: {}", String.valueOf(ex.getCause()));
        }
        return errorWriter.write(exchange, errorCode);
    }
}
