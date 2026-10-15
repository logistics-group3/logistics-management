package com.sparta.logistics.delivery.application.dto;

import com.sparta.logistics.delivery.domain.Delivery;
import com.sparta.logistics.delivery.domain.DeliveryStatus;
import java.time.LocalDateTime;
import java.util.UUID;

/** D-01 목록 원소. 구간을 담지 않아 목록 조회에서 구간을 읽지 않는다. */
public record DeliverySummaryResult(
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

    public static DeliverySummaryResult from(Delivery delivery) {
        return new DeliverySummaryResult(delivery.getId(), delivery.getOrderId(), delivery.getStatus(),
                delivery.getSupplierCompanyId(), delivery.getReceiverCompanyId(),
                delivery.getDepartureHubId(), delivery.getDestinationHubId(),
                delivery.getPickupAddress(), delivery.getDeliveryAddress(),
                delivery.getRecipientName(), delivery.getRecipientSlackId(),
                delivery.getCreatedAt(), delivery.getUpdatedAt());
    }
}
