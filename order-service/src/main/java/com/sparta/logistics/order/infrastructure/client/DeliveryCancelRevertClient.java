package com.sparta.logistics.order.infrastructure.client;

import com.sparta.logistics.common.feign.IdempotentRetryConfig;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "delivery-service", contextId = "order-delivery-cancel-revert", path = "/internal",
        configuration = IdempotentRetryConfig.class)
public interface DeliveryCancelRevertClient {

    /** I-12. 원복 완료 후 같은 요청은 no-op이며 응답 없는 통신 실패만 재시도한다. */
    @PostMapping("/deliveries/{deliveryId}/cancel-revert")
    void revert(@PathVariable("deliveryId") UUID deliveryId);
}
