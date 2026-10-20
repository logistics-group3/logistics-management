package com.sparta.logistics.order.infrastructure.client;

import com.sparta.logistics.order.infrastructure.client.config.DeliveryNoRetryFeignConfig;
import com.sparta.logistics.order.infrastructure.client.dto.DeliveryOrderStateResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.ResponseEntity;

@FeignClient(name = "delivery-service", contextId = "order-delivery-state", path = "/internal",
        configuration = DeliveryNoRetryFeignConfig.class)
public interface DeliveryOrderStateClient {

    /** I-17. 보상 직후 한 번만 확인한다. 실패하면 복구 필요로 남기므로 재시도하지 않고 타임아웃을 짧게 둔다. */
    @GetMapping("/deliveries/orders/{orderId}")
    DeliveryOrderStateResponse getByOrderId(@PathVariable("orderId") UUID orderId);

    @DeleteMapping("/deliveries/orders/{orderId}/creation")
    ResponseEntity<Void> closeCreation(@PathVariable("orderId") UUID orderId,
            @RequestParam(value = "expectedDeliveryId", required = false) UUID expectedDeliveryId);
}
