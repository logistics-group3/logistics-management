package com.sparta.logistics.delivery.infrastructure.client.dto;

import java.util.UUID;

/** I-21 응답에서 배송이 쓰는 필드 */
public record OrderCreationResultResponse(UUID orderId, String result, String orderStatus) {
}
