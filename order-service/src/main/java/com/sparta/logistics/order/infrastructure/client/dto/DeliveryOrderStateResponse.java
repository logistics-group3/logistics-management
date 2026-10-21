package com.sparta.logistics.order.infrastructure.client.dto;

import java.util.UUID;

/** I-17에서 사용하는 필드. state는 ACTIVE, CLOSED, NONE이다. 내부 응답은 ApiResponse로 감싸지 않는다. */
public record DeliveryOrderStateResponse(UUID orderId, String state, UUID deliveryId, String status) {
}
