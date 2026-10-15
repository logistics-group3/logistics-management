package com.sparta.logistics.order.presentation.dto;

import com.sparta.logistics.order.application.dto.CancelOrderResult;
import com.sparta.logistics.order.domain.OrderStatus;
import java.util.UUID;

public record CancelOrderResponse(UUID orderId, OrderStatus status) {

    public static CancelOrderResponse from(CancelOrderResult result) {
        return new CancelOrderResponse(result.orderId(), result.status());
    }
}
