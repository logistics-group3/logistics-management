package com.sparta.logistics.common.feign;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;

/**
 * Feign 호출 메트릭 이름을 {@code http.client.requests}에서 {@code feign.client.requests}로 바꾼다(ADR 0018).
 * Note: Prometheus는 같은 이름의 메트릭이 모두 같은 라벨 키를 가져야 한다. Eureka 클라이언트가 먼저 등록한 http.client.requests
 * (client_name, method, uri, status)와 Feign 계측의 라벨 키(clientName, http.method, http.url, http.status_code)가 달라서,
 * 이름이 같으면 Feign 메트릭은 등록이 거부되고 서비스 간 호출 지표가 통째로 빠진다. 이름을 나눠 둘 다 남긴다.
 */
public class FeignMeterFilter implements MeterFilter {

    static final String SOURCE = "http.client.requests";
    static final String TARGET = "feign.client.requests";
    private static final String FEIGN_CLIENT_TAG = "clientName";

    @Override
    public Meter.Id map(Meter.Id id) {
        if (id.getName().startsWith(SOURCE) && id.getTag(FEIGN_CLIENT_TAG) != null) {
            return id.withName(TARGET + id.getName().substring(SOURCE.length()));
        }
        return id;
    }
}
