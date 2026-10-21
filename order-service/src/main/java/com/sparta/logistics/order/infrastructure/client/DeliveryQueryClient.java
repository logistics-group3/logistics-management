package com.sparta.logistics.order.infrastructure.client;

import com.sparta.logistics.common.feign.IdempotentRetryConfig;
import com.sparta.logistics.order.infrastructure.client.dto.DeliveryLookupResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "delivery-service", contextId = "order-delivery-read", path = "/internal",
        configuration = IdempotentRetryConfig.class)
public interface DeliveryQueryClient {
    /** I-10. 읽기 요청의 응답 없는 통신 실패만 최대 3회 시도한다. */
    @GetMapping("/deliveries/{deliveryId}")
    DeliveryLookupResponse getDelivery(@PathVariable("deliveryId") UUID deliveryId);
}
