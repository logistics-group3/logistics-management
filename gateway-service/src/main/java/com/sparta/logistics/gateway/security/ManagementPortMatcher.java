package com.sparta.logistics.gateway.security;

import java.net.InetSocketAddress;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 관리 서버(management.server.port)로 들어온 요청을 고른다(ADR 0018).
 * Note: 설정값이 아니라 실제로 뜬 포트를 쓴다. 설정이 0(무작위)이어도 맞고, 관리 포트를 따로 두지 않으면 아무 요청도 고르지 않는다.
 * 관리 서버는 자식 컨텍스트로 뜨고, 그 기동 이벤트가 부모 컨텍스트의 이 리스너에도 전달된다.
 */
public class ManagementPortMatcher implements ServerWebExchangeMatcher, ApplicationListener<WebServerInitializedEvent> {

    private static final String MANAGEMENT_NAMESPACE = "management";

    private volatile int port = -1;

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        if (WebServerApplicationContext.hasServerNamespace(event.getApplicationContext(), MANAGEMENT_NAMESPACE)) {
            port = event.getWebServer().getPort();
        }
    }

    @Override
    public Mono<MatchResult> matches(ServerWebExchange exchange) {
        InetSocketAddress local = exchange.getRequest().getLocalAddress();
        return port > 0 && local != null && local.getPort() == port ? MatchResult.match() : MatchResult.notMatch();
    }
}
