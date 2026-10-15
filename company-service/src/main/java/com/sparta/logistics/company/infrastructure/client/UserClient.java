package com.sparta.logistics.company.infrastructure.client;

import com.sparta.logistics.common.feign.IdempotentRetryConfig;
import com.sparta.logistics.company.infrastructure.client.dto.UserResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", path = "/internal", configuration = IdempotentRetryConfig.class)
public interface UserClient {

    /** I-01 사용자 조회 (제공: user-service) */
    @GetMapping("/users/{userId}")
    UserResponse getUser(@PathVariable UUID userId);
}
