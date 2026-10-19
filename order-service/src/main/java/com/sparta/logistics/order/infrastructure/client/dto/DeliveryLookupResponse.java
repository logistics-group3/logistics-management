package com.sparta.logistics.order.infrastructure.client.dto;

import java.util.UUID;

/** I-10에서 사용하는 필드. 내부 응답은 ApiResponse로 감싸지 않는다. */
public record DeliveryLookupResponse(UUID deliveryId, UUID orderId, String status) {
}
