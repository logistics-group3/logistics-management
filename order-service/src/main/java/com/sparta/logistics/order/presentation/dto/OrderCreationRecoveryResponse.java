package com.sparta.logistics.order.presentation.dto;

import com.sparta.logistics.order.application.dto.OrderCreationRecoveryResult;
import com.sparta.logistics.order.domain.OrderCreationRecovery;
import com.sparta.logistics.order.domain.OrderCreationStatus;
import java.util.List;
import java.util.UUID;

public record OrderCreationRecoveryResponse(UUID recoveryId, UUID orderId, UUID attemptId,
        OrderCreationRecovery.Status status, UUID executionId, UUID requestedBy,
        OrderCreationStatus expectedStatus, String reason, String message, List<OrderCreationRecoveryResult.Event> events) {
    public static OrderCreationRecoveryResponse from(OrderCreationRecoveryResult result) {
        return new OrderCreationRecoveryResponse(result.recoveryId(), result.orderId(), result.attemptId(),
                result.status(), result.executionId(), result.requestedBy(), result.expectedStatus(), result.reason(),
                result.message(), result.events());
    }
}
