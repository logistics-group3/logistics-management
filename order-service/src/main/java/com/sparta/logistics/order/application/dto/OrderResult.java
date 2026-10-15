package com.sparta.logistics.order.application.dto;

import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.domain.OrderCreationSnapshot;
import com.sparta.logistics.order.domain.OrderStatus;
import java.time.LocalDateTime;
import java.util.UUID;

/** 주문 application 결과(SA 6.7). presentation은 별도 응답 DTO로 변환한다. */
public record OrderResult(UUID orderId, UUID ordererId, UUID supplierCompanyId, UUID receiverCompanyId,
                          UUID productId, String productName, long unitPrice, int quantity, long totalPrice,
                          String requestNote, OrderStatus status, UUID deliveryId,
                          LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static OrderResult from(Order order) {
        return new OrderResult(order.getId(), order.getOrdererId(), order.getSupplierCompanyId(),
                order.getReceiverCompanyId(), order.getProductId(), order.getProductName(), order.getUnitPrice(),
                order.getQuantity(), order.getTotalPrice(), order.getRequestNote(), order.getStatus(),
                order.getDeliveryId(), order.getCreatedAt(), order.getUpdatedAt());
    }

    public static OrderResult from(OrderCreationSnapshot snapshot) {
        return new OrderResult(snapshot.orderId(), snapshot.ordererId(), snapshot.supplierCompanyId(),
                snapshot.receiverCompanyId(), snapshot.productId(), snapshot.productName(), snapshot.unitPrice(),
                snapshot.quantity(), snapshot.totalPrice(), snapshot.requestNote(), snapshot.status(),
                snapshot.deliveryId(), snapshot.createdAt(), snapshot.updatedAt());
    }
}
