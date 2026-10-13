package com.sparta.logistics.gateway.auth;

import java.util.UUID;

/** I-01 응답 중 Gateway가 쓰는 값. */
public record UserSnapshot(UUID userId, String username, String role, boolean isDeliveryManager) {
}
