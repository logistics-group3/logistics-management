package com.sparta.logistics.order.infrastructure.client.dto;

import java.util.UUID;

/** I-09 응답 중 주문 저장에 필요한 배송 ID만 사용한다. 배송 상태는 delivery가 관리한다. */
public record DeliveryResponse(UUID deliveryId) {
}
