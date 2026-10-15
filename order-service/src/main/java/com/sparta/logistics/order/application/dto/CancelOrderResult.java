package com.sparta.logistics.order.application.dto;

import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.domain.OrderStatus;
import java.util.UUID;

public record CancelOrderResult(UUID orderId, OrderStatus status) {

    public static CancelOrderResult from(Order order) {
        return new CancelOrderResult(order.getId(), order.getStatus());
    }
}
