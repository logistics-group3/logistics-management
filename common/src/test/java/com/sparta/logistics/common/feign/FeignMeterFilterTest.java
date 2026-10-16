package com.sparta.logistics.common.feign;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeignMeterFilterTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();

    FeignMeterFilterTest() {
        registry.config().meterFilter(new FeignMeterFilter());
    }

    @Test
    @DisplayName("Feign 계측 메트릭(clientName 태그)은 feign.client.requests로 이름이 바뀐다. active 접미사도 유지한다")
    void renamesFeignMeters() {
        registry.timer("http.client.requests", "clientName", "CompanyClient", "http.method", "GET").record(java.time.Duration.ofMillis(5));
        registry.more().longTaskTimer("http.client.requests.active", "clientName", "CompanyClient");

        assertThat(registry.find("feign.client.requests").tag("clientName", "CompanyClient").timer()).isNotNull();
        assertThat(registry.find("feign.client.requests.active").longTaskTimer()).isNotNull();
        assertThat(registry.find("http.client.requests").timer()).isNull();
    }

    @Test
    @DisplayName("다른 HTTP 클라이언트(Eureka, RestClient)의 메트릭 이름은 그대로 둔다")
    void keepsOtherClients() {
        registry.timer("http.client.requests", "client.name", "eureka-server", "method", "GET").record(java.time.Duration.ofMillis(5));

        assertThat(registry.find("http.client.requests").tag("client.name", "eureka-server").timer()).isNotNull();
        assertThat(registry.find("feign.client.requests").timer()).isNull();
    }
}
