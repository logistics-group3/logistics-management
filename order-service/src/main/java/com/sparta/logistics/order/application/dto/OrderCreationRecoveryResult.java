package com.sparta.logistics.order.application.dto;

import com.sparta.logistics.order.domain.OrderCreationRecovery;
import com.sparta.logistics.order.domain.OrderCreationRecoveryEvent;
import com.sparta.logistics.order.domain.OrderCreationStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderCreationRecoveryResult(UUID recoveryId, UUID orderId, UUID attemptId,
        OrderCreationRecovery.Status status, UUID executionId, UUID requestedBy,
        OrderCreationStatus expectedStatus, String reason, String message, List<Event> events) {
    public static OrderCreationRecoveryResult from(OrderCreationRecovery recovery, List<OrderCreationRecoveryEvent> events) {
        return new OrderCreationRecoveryResult(recovery.getId(), recovery.getOrderId(), recovery.getAttemptId(),
                recovery.getStatus(), recovery.getExecutionId(), recovery.getRequestedBy(), recovery.getExpectedStatus(),
                recovery.getReason(), recovery.getMessage(), events.stream().map(Event::from).toList());
    }

    public record Event(String step, String detail, UUID executionId, LocalDateTime occurredAt, String actor) {
        static Event from(OrderCreationRecoveryEvent event) {
            return new Event(event.getStep(), event.getDetail(), event.getExecutionId(), event.getCreatedAt(), event.getCreatedBy());
        }
    }
}
