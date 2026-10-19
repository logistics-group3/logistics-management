package com.sparta.logistics.order.infrastructure.client;

import com.sparta.logistics.common.feign.IdempotentRetryConfig;
import com.sparta.logistics.order.infrastructure.client.dto.CreateDeliveryRequest;
import com.sparta.logistics.order.infrastructure.client.dto.DeliveryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "delivery-service", contextId = "order-delivery-create", path = "/internal",
        configuration = IdempotentRetryConfig.class)
public interface DeliveryCreateClient {

    /** I-09. orderId로 멱등하므로 응답 없는 실패를 재시도할 수 있다. */
    @PostMapping(value = "/deliveries", consumes = MediaType.APPLICATION_JSON_VALUE)
    DeliveryResponse create(@RequestBody CreateDeliveryRequest request);
}
