package com.sparta.logistics.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 메트릭을 공개 포트와 분리한 관리 포트로만 내보내는지 확인한다(ADR 0018).
 * Prometheus는 토큰 없이 관리 포트에서 가져가고, 외부 공개 포트에서는 같은 경로가 인증에 막혀야 한다.
 * Note: @SpringBootTest는 기본으로 메트릭 내보내기를 끈다. @AutoConfigureObservability로 켜야 /actuator/prometheus가 생긴다.
 */
@AutoConfigureObservability
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.config.enabled=false",
                "eureka.client.enabled=false",
                "jwt.secret=" + GatewaySecurityTest.SECRET,
                "auth.whitelist[0]=/api/v1/auth/login",
                "auth.user-cache-ttl=60s",
                "management.endpoints.web.exposure.include=health,prometheus",
                "spring.cloud.gateway.server.webflux.routes[0].id=missing",
                "spring.cloud.gateway.server.webflux.routes[0].uri=lb://missing-service",
                "spring.cloud.gateway.server.webflux.routes[0].predicates[0]=Path=/api/**"
        })
class GatewayManagementPortTest {

    // Note: 0(무작위)을 쓰면 Gateway가 관리 포트를 설정값으로 판단하는 부분이 실제와 달라진다. 운영처럼 고정 포트를 하나 골라 넣는다.
    @DynamicPropertySource
    static void managementPort(DynamicPropertyRegistry registry) {
        registry.add("management.server.port", () -> TestSocketUtils.findAvailableTcpPort());
    }

    @LocalServerPort
    private int serverPort;

    @LocalManagementPort
    private int managementPort;

    @MockitoBean
    private UserLookup userLookup;

    private static WebTestClient client(int port) {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    @DisplayName("관리 포트의 /actuator/prometheus는 토큰 없이 Prometheus 형식으로 응답한다")
    void managementPortServesMetricsWithoutToken() {
        client(managementPort).get().uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("jvm_memory_used_bytes"));
    }

    @Test
    @DisplayName("공개 포트에서 같은 경로는 401이다. 메트릭이 외부로 나가지 않는다")
    void publicPortDoesNotExposeMetrics() {
        client(serverPort).get().uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("관리 포트로 API 경로를 부르면 토큰 검증을 건너뛰지 않는다. 인증 우회 경로가 되지 않는다")
    void managementPortStillRequiresTokenForApi() {
        client(managementPort).get().uri("/api/v1/hubs")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
