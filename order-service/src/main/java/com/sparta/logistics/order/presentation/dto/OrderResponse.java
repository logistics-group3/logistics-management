package com.sparta.logistics.order.presentation.dto;

import com.sparta.logistics.order.application.dto.OrderResult;
import com.sparta.logistics.order.domain.OrderStatus;
import java.time.LocalDateTime;
import java.util.UUID;

/** 외부 주문 응답 계약(SA 6.7). Entity와 application Result를 직접 노출하지 않는다. */
public record OrderResponse(UUID orderId, UUID ordererId, UUID supplierCompanyId, UUID receiverCompanyId,
                            UUID productId, String productName, long unitPrice, int quantity, long totalPrice,
                            String requestNote, OrderStatus status, UUID deliveryId,
                            LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static OrderResponse from(OrderResult result) {
        return new OrderResponse(result.orderId(), result.ordererId(), result.supplierCompanyId(), result.receiverCompanyId(),
                result.productId(), result.productName(), result.unitPrice(), result.quantity(), result.totalPrice(),
                result.requestNote(), result.status(), result.deliveryId(), result.createdAt(), result.updatedAt());
    }
}
