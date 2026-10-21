package com.sparta.logistics.order.application.dto;

import com.sparta.logistics.order.domain.OrderCreationStatus;
import java.util.UUID;

/**
 * 주문 생성 접수(O-01)의 결과. 새로 접수했거나 같은 키가 아직 처리 중이면 order가 null이고(202),
 * 같은 키가 이미 완료됐으면 최초 응답 스냅샷이 담긴다(201).
 */
public record OrderCreationOutcome(UUID orderId, OrderCreationStatus status, OrderResult order) {

    public static OrderCreationOutcome accepted(UUID orderId) {
        return new OrderCreationOutcome(orderId, OrderCreationStatus.PROCESSING, null);
    }

    public static OrderCreationOutcome completed(OrderResult order) {
        return new OrderCreationOutcome(order.orderId(), OrderCreationStatus.COMPLETED, order);
    }
}
