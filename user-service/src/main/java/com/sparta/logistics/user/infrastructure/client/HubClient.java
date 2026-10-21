package com.sparta.logistics.user.infrastructure.client;

import com.sparta.logistics.common.feign.IdempotentRetryConfig;
import com.sparta.logistics.user.infrastructure.client.dto.HubResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** GET이라 연결 실패·타임아웃 때 재시도해도 결과가 같다(SA 3.5). */
@FeignClient(name = "hub-service", path = "/internal", configuration = IdempotentRetryConfig.class)
public interface HubClient {

    /** I-02 허브 조회 (제공: hub-service) */
    @GetMapping("/hubs/{hubId}")
    HubResponse getHub(@PathVariable UUID hubId);
}
