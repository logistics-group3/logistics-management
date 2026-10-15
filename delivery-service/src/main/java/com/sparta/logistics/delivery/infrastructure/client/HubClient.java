package com.sparta.logistics.delivery.infrastructure.client;

import com.sparta.logistics.common.feign.IdempotentRetryConfig;
import com.sparta.logistics.delivery.infrastructure.client.dto.HubRouteResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** hub-service 내부 API. GET이라 응답 없는 실패는 재시도한다(SA 3.5). */
@FeignClient(name = "hub-service", path = "/internal", configuration = IdempotentRetryConfig.class)
public interface HubClient {

    /** I-03 허브 간 경로 1건 (제공: hub-service) */
    @GetMapping("/hub-routes")
    HubRouteResponse getHubRoute(@RequestParam("departureHubId") UUID departureHubId,
                                 @RequestParam("arrivalHubId") UUID arrivalHubId);
}
