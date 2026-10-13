package com.sparta.logistics.gateway.filter;

import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.gateway.support.ErrorResponseWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * /internal/** 외부 차단 (SA 8.3 - 2단계). 서비스 간 Feign 전용 경로라 외부에서는 404 로 응답한다.
 *
 * GlobalFilter 가 아니라 WebFilter 인 이유: GlobalFilter 는 라우트가 매칭된 요청에만 실행된다.
 * /internal/** 은 라우트가 없으므로 라우팅 전에 동작하는 WebFilter 로 확실하게 막는다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class InternalPathBlockFilter implements WebFilter {

    private static final String INTERNAL_PREFIX = "/internal";

    private final ErrorResponseWriter errorResponseWriter;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (path.equals(INTERNAL_PREFIX) || path.startsWith(INTERNAL_PREFIX + "/")) {
            return errorResponseWriter.write(exchange, CommonErrorCode.NOT_FOUND);
        }
        return chain.filter(exchange);
    }
}
