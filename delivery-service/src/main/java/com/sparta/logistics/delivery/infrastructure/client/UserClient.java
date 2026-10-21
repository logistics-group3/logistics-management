package com.sparta.logistics.delivery.infrastructure.client;

import com.sparta.logistics.delivery.infrastructure.config.ConnectFailureRetryConfig;
import com.sparta.logistics.delivery.infrastructure.client.dto.DeliveryManagerResponse;
import com.sparta.logistics.delivery.infrastructure.client.dto.UserResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/** user-service 내부 API. 연결 실패만 한 번 더 시도한다(결정 로그 26, ConnectFailureRetryer). 읽기 타임아웃은 재시도하지 않는다. */
@FeignClient(name = "user-service", path = "/internal/users", configuration = ConnectFailureRetryConfig.class)
public interface UserClient {

    /** I-01 사용자 조회 (제공: user-service) */
    @GetMapping("/{userId}")
    UserResponse getUser(@PathVariable("userId") UUID userId);

    /** I-14 허브별 배송담당자 목록 (제공: user-service). 없으면 빈 배열 */
    @GetMapping("/delivery-managers")
    List<DeliveryManagerResponse> getDeliveryManagers(@RequestParam("hubId") UUID hubId);
}
