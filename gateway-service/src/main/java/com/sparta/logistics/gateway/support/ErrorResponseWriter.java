package com.sparta.logistics.gateway.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** SA 6.1 에러 형식(status, code, message, path, timestamp)으로 Gateway가 직접 응답한다. */
@Component
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> write(ServerWebExchange exchange, GatewayErrorCode errorCode) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", errorCode.status().value());
        body.put("code", errorCode.name());
        body.put("message", errorCode.message());
        body.put("path", exchange.getRequest().getPath().value());
        body.put("timestamp", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(errorCode.status());
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            DataBuffer buffer = response.bufferFactory().wrap(objectMapper.writeValueAsBytes(body));
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }
}
