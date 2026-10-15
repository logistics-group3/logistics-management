package com.sparta.logistics.delivery.presentation.dto;

import com.sparta.logistics.delivery.application.dto.DeliverySummaryResult;
import com.sparta.logistics.delivery.domain.DeliveryStatus;
import java.time.LocalDateTime;
import java.util.UUID;

/** D-01 목록 원소: routes를 뺀 DeliveryResponse(SA 6.8). */
public record DeliverySummaryResponse(
        UUID deliveryId,
        UUID orderId,
        DeliveryStatus status,
        UUID supplierCompanyId,
        UUID receiverCompanyId,
        UUID departureHubId,
        UUID destinationHubId,
        String pickupAddress,
        String deliveryAddress,
        String recipientName,
        String recipientSlackId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static DeliverySummaryResponse from(DeliverySummaryResult result) {
        return new DeliverySummaryResponse(result.deliveryId(), result.orderId(), result.status(),
                result.supplierCompanyId(), result.receiverCompanyId(), result.departureHubId(), result.destinationHubId(),
                result.pickupAddress(), result.deliveryAddress(), result.recipientName(), result.recipientSlackId(),
                result.createdAt(), result.updatedAt());
    }
}
