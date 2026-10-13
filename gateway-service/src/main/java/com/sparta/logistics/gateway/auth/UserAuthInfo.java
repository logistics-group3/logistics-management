package com.sparta.logistics.gateway.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sparta.logistics.common.auth.UserRole;
import java.util.UUID;

/** user-service I-01 (GET /internal/users/{userId}) 응답 중 Gateway 가 쓰는 값 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserAuthInfo(
        UUID userId,
        String username,
        UserRole role,
        @JsonProperty("isDeliveryManager") boolean isDeliveryManager
) {
}
