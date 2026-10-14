package com.sparta.logistics.gateway.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Gateway 단계 예외를 공통 에러 형식으로 변환한다 (ADR-15). 기본 핸들러(-1)보다 먼저 실행한다. */
@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
public class GatewayErrorHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatusCode status;
        String code;
        String message;
        if (ex instanceof GatewayException ge) {
            status = ge.getStatus();
            code = ge.getCode();
            message = ge.getMessage();
        } else if (ex instanceof ResponseStatusException rse) {
            status = rse.getStatusCode();
            code = toCode(status);
            message = rse.getReason() != null ? rse.getReason() : code;
        } else {
            log.error("Gateway error. path={}", exchange.getRequest().getPath(), ex);
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            code = "INTERNAL_SERVER_ERROR";
            message = "서버 내부 오류가 발생했습니다.";
        }

        GatewayErrorResponse body = new GatewayErrorResponse(status.value(), code, message,
                exchange.getRequest().getPath().value(), LocalDateTime.now());

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(objectMapper.writeValueAsBytes(body));
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }

    private String toCode(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 503 -> "SERVICE_UNAVAILABLE";
            default -> status.is4xxClientError() ? "INVALID_REQUEST" : "INTERNAL_SERVER_ERROR";
        };
    }
}
