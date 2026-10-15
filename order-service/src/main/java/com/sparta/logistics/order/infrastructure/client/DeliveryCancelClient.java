package com.sparta.logistics.order.infrastructure.client;

import com.sparta.logistics.order.infrastructure.client.config.DeliveryNoRetryFeignConfig;
import com.sparta.logistics.order.infrastructure.client.dto.DeliveryStatusResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "delivery-service", contextId = "order-delivery-cancel", path = "/internal",
        configuration = DeliveryNoRetryFeignConfig.class)
public interface DeliveryCancelClient {

    /** I-11. 배송 상태 확인과 취소는 delivery의 단일 트랜잭션에서 처리한다. */
    @PostMapping("/deliveries/{deliveryId}/cancel")
    DeliveryStatusResponse cancel(@PathVariable("deliveryId") UUID deliveryId);
}
