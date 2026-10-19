package com.sparta.logistics.order.presentation.dto;

import com.sparta.logistics.order.application.dto.OrderCreationOutcome;
import com.sparta.logistics.order.domain.OrderCreationStatus;
import java.util.UUID;

/** O-01 접수 응답(202). 처리 결과는 GET /api/v1/orders/creations/{orderId}로 확인한다. */
public record OrderAcceptedResponse(UUID orderId, OrderCreationStatus status) {

    public static OrderAcceptedResponse from(OrderCreationOutcome outcome) {
        return new OrderAcceptedResponse(outcome.orderId(), outcome.status());
    }
}
