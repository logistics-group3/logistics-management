package com.sparta.logistics.order.application.dto;

import com.sparta.logistics.order.domain.OrderCancellation;
import java.util.UUID;

public record CancellationAttempt(UUID attemptId, UUID orderId, UUID deliveryId) {

    public static CancellationAttempt from(OrderCancellation cancellation) {
        return new CancellationAttempt(cancellation.getId(), cancellation.getOrderId(), cancellation.getDeliveryId());
    }
}
