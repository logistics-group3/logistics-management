package com.sparta.logistics.hub.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.hub.infrastructure.client.ProductClient;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * config-repo의 company-service Feign 타임아웃(읽기 2초)이 공통 기본값(20초)을 덮어쓰는지 응답하지 않는 서버로 확인한다.
 * 타임아웃이 적용되면 시도 하나가 2초에 끊기고 재시도 3회를 합쳐 10초 안에 끝난다. 적용되지 않으면 60초 가까이 걸린다.
 * Note: 운영 설정 파일(application.yml, hub-service.yml)을 그대로 불러와 키 이름이 틀리면 이 테스트가 실패한다.
 */
@TestPropertySource(properties = "spring.config.import=file:../config-repo/application.yml,file:../config-repo/hub-service.yml")
class ProductClientTimeoutIntegrationTest extends PostgresIntegrationTest {

    private static final AtomicInteger REQUESTS = new AtomicInteger();
    private static final HttpServer SLOW_SERVER = startSlowServer();

    @Autowired
    private ProductClient productClient;

    @DynamicPropertySource
    static void companyUrl(DynamicPropertyRegistry registry) {
        // 운영 설정의 hub_schema는 테스트 컨테이너에 없으므로 스키마만 기본값으로 되돌린다.
        registry.add("spring.flyway.schemas", () -> "public");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "public");
        registry.add("spring.cloud.openfeign.client.config.company-service.url",
                () -> "http://localhost:" + SLOW_SERVER.getAddress().getPort());
    }

    @AfterAll
    static void stopServer() {
        SLOW_SERVER.stop(0);
    }

    @BeforeEach
    void resetRequests() {
        REQUESTS.set(0);
    }

    @Test
    @DisplayName("I-07(업체 조회)도 같은 타임아웃을 적용받는다")
    void companyLookupTimesOutQuickly() {
        long start = System.nanoTime();

        assertThatThrownBy(() -> productClient.getCompany(UUID.randomUUID()));

        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        assertThat(REQUESTS.get()).isEqualTo(3);
        assertThat(elapsedMillis).isBetween(5_500L, 10_000L);
    }

    @Test
    @DisplayName("응답이 없는 company는 읽기 2초에 끊기고 재시도 3회를 합쳐 10초 안에 실패한다")
    void companyClientTimesOutQuickly() {
        long start = System.nanoTime();

        assertThatThrownBy(() -> productClient.getProduct(UUID.randomUUID()));

        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        assertThat(REQUESTS.get()).isEqualTo(3);
        assertThat(elapsedMillis).isBetween(5_500L, 10_000L);
    }

    private static HttpServer startSlowServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                REQUESTS.incrementAndGet();
                try {
                    Thread.sleep(8_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                exchange.close();
            });
            // Note: 기본 실행기는 스레드가 하나라 재시도 요청이 앞 요청의 sleep 뒤에야 처리되어 시도 횟수를 셀 수 없다.
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            return server;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
