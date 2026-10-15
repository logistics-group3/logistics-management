package com.sparta.logistics.delivery.infrastructure.client;

import com.sparta.logistics.common.feign.IdempotentRetryConfig;
import com.sparta.logistics.delivery.infrastructure.client.dto.DeliveryManagerResponse;
import com.sparta.logistics.delivery.infrastructure.client.dto.UserResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/** user-service 내부 API. 둘 다 GET이라 응답 없는 실패(연결 실패·타임아웃)는 재시도한다(SA 3.5). */
@FeignClient(name = "user-service", path = "/internal/users", configuration = IdempotentRetryConfig.class)
public interface UserClient {

    /** I-01 사용자 조회 (제공: user-service) */
    @GetMapping("/{userId}")
    UserResponse getUser(@PathVariable("userId") UUID userId);

    /** I-14 허브별 배송담당자 목록 (제공: user-service). 없으면 빈 배열 */
    @GetMapping("/delivery-managers")
    List<DeliveryManagerResponse> getDeliveryManagers(@RequestParam("hubId") UUID hubId);
}
