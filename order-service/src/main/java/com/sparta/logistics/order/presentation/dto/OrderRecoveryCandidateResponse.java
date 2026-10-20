package com.sparta.logistics.order.presentation.dto;

import com.sparta.logistics.order.application.dto.OrderRecoveryCandidateResult;
import com.sparta.logistics.order.domain.OrderCreationStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record OrderRecoveryCandidateResponse(UUID attemptId, UUID orderId, OrderCreationStatus status,
        UUID recoveryId, UUID deliveryId, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static OrderRecoveryCandidateResponse from(OrderRecoveryCandidateResult result) {
        return new OrderRecoveryCandidateResponse(result.attemptId(), result.orderId(), result.status(), result.recoveryId(),
                result.deliveryId(), result.createdAt(), result.updatedAt());
    }
}
