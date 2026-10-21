package com.sparta.logistics.order.infrastructure.client.notification;

import com.sparta.logistics.common.feign.IdempotentRetryConfig;
import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "delivery-service", contextId = "order-dm-delivery", configuration = IdempotentRetryConfig.class)
public interface NotificationDeliveryClient {
    @GetMapping("/internal/deliveries/{deliveryId}/notification-recipients")
    Response get(@PathVariable UUID deliveryId);
    record Response(UUID deliveryId, UUID orderId, String status, List<UUID> managerIds) { }
}
