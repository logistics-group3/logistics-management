package com.sparta.logistics.order.infrastructure.client.notification;

import com.sparta.logistics.common.feign.IdempotentRetryConfig;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", contextId = "order-dm-user", configuration = IdempotentRetryConfig.class)
public interface NotificationUserClient {
    @GetMapping("/internal/users/{userId}/notification-contact")
    Response get(@PathVariable UUID userId);
    record Response(UUID userId, boolean isDeliveryManager, String slackEmail) { }
}
