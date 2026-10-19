package com.sparta.logistics.order.infrastructure.client;

import com.sparta.logistics.order.infrastructure.client.config.DeliveryNoRetryFeignConfig;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "delivery-service", contextId = "order-delivery-delete", path = "/internal",
        configuration = DeliveryNoRetryFeignConfig.class)
public interface DeliveryDeleteClient {

    /** I-13. 보상 재시도는 OrderService가 맡으므로 이 클라이언트는 한 번만 호출한다. */
    @DeleteMapping("/deliveries/orders/{orderId}")
    ResponseEntity<Void> deleteByOrderId(@PathVariable("orderId") UUID orderId);
}
