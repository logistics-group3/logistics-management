package com.sparta.logistics.order.infrastructure.client.dto;

/** I-11의 취소 성공 여부만 사용한다. 배송 상태 전체를 주문 도메인의 enum으로 복제하지 않는다. */
public record DeliveryStatusResponse(String status) {
}
