package com.sparta.logistics.order.infrastructure.client;

import com.sparta.logistics.common.feign.IdempotentRetryConfig;
import com.sparta.logistics.order.infrastructure.client.dto.DeliveryStatusResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "delivery-service", contextId = "order-delivery-confirm", path = "/internal",
        configuration = IdempotentRetryConfig.class)
public interface DeliveryConfirmClient {

    /** I-19 주문 확정. orderId로 멱등하므로 응답 없는 실패를 재시도할 수 있다. */
    @PostMapping("/deliveries/orders/{orderId}/confirm")
    DeliveryStatusResponse confirm(@PathVariable("orderId") UUID orderId);
}
