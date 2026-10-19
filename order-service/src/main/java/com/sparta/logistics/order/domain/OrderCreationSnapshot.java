package com.sparta.logistics.order.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/** 최초 생성 결과. Order가 수정·취소돼도 재요청의 생성 응답은 이 불변 값을 사용한다. */
public record OrderCreationSnapshot(UUID orderId, UUID ordererId, UUID supplierCompanyId, UUID receiverCompanyId,
        UUID productId, String productName, long unitPrice, int quantity, long totalPrice, String requestNote,
        OrderStatus status, UUID deliveryId, LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static OrderCreationSnapshot from(Order order) {
        return new OrderCreationSnapshot(order.getId(), order.getOrdererId(), order.getSupplierCompanyId(),
                order.getReceiverCompanyId(), order.getProductId(), order.getProductName(), order.getUnitPrice(),
                order.getQuantity(), order.getTotalPrice(), order.getRequestNote(), order.getStatus(),
                order.getDeliveryId(), order.getCreatedAt(), order.getUpdatedAt());
    }
}
