package com.sparta.logistics.order.application.dto;

import com.sparta.logistics.order.domain.OrderCreation;
import com.sparta.logistics.order.domain.OrderCreationStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record OrderRecoveryCandidateResult(UUID attemptId, UUID orderId, OrderCreationStatus status,
        UUID recoveryId, UUID deliveryId, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static OrderRecoveryCandidateResult from(OrderCreation creation) {
        return new OrderRecoveryCandidateResult(creation.getId(), creation.getOrderId(), creation.getStatus(),
                creation.getRecoveryId(), creation.getDeliveryId(), creation.getCreatedAt(), creation.getUpdatedAt());
    }
}
