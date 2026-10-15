package com.sparta.logistics.delivery.infrastructure.client.dto;

import java.util.UUID;

/** user I-01 응답 중 배송이 쓰는 필드만 둔다(SA 6.3). */
public record UserResponse(UUID userId, boolean isDeliveryManager, UUID hubId, String slackId) {
}
