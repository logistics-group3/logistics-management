package com.sparta.logistics.gateway.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway에서 직접 거절할 때도 서비스와 같은 에러 형식(SA 6.1)으로 응답한다.
 * { "status", "code", "message", "path", "timestamp" }
 */
@Component
@RequiredArgsConstructor
public class GatewayErrorWriter {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final ObjectMapper objectMapper;

    public Mono<Void> write(ServerWebExchange exchange, GatewayError error) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(error.status());
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", error.status().value());
        body.put("code", error.name());
        body.put("message", error.message());
        body.put("path", exchange.getRequest().getPath().value());
        body.put("timestamp", LocalDateTime.now().format(TIMESTAMP));

        DataBuffer buffer = response.bufferFactory().wrap(toBytes(body));
        return response.writeWith(Mono.just(buffer));
    }

    private byte[] toBytes(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            return ("{\"code\":\"INTERNAL_ERROR\"}").getBytes(StandardCharsets.UTF_8);
        }
    }

    /** Gateway가 직접 내보내는 에러 (code는 common 모듈 CommonErrorCode와 같은 이름) */
    public enum GatewayError {
        UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다. 유효한 토큰을 보내주세요."),
        TOKEN_ROLE_MISMATCH(HttpStatus.UNAUTHORIZED, "권한 정보가 변경되었거나 탈퇴한 사용자입니다. 다시 로그인해 주세요."),
        RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
        SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "인증 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.");

        private final HttpStatus status;
        private final String message;

        GatewayError(HttpStatus status, String message) {
            this.status = status;
            this.message = message;
        }

        public HttpStatus status() {
            return status;
        }

        public String message() {
            return message;
        }
    }
}
