package com.sparta.logistics.delivery.infrastructure.client;

import com.sparta.logistics.delivery.infrastructure.config.ConnectFailureRetryConfig;
import com.sparta.logistics.delivery.infrastructure.client.dto.HubRouteResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** hub-service 내부 API. 연결 실패만 한 번 더 시도한다(결정 로그 26, ConnectFailureRetryer). */
@FeignClient(name = "hub-service", path = "/internal", configuration = ConnectFailureRetryConfig.class)
public interface HubClient {

    /** I-03 허브 간 경로 1건 (제공: hub-service) */
    @GetMapping("/hub-routes")
    HubRouteResponse getHubRoute(@RequestParam("departureHubId") UUID departureHubId,
                                 @RequestParam("arrivalHubId") UUID arrivalHubId);
}
