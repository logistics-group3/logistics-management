package com.sparta.logistics.delivery.application.dto;

import com.querydsl.core.annotations.QueryProjection;
import com.sparta.logistics.delivery.domain.Delivery;
import com.sparta.logistics.delivery.domain.DeliveryStatus;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * D-01 목록 원소이자 D-02 상세의 배송 부분. 구간을 담지 않아 목록 조회에서 구간을 읽지 않는다.
 * Note: 조회는 DeliveryQueryRepository가 이 record를 @QueryProjection으로 바로 만든다(Entity를 거치지 않음, 결정 로그 17).
 * from(Delivery)는 명령(D-03~D-05) 뒤 이미 가진 Entity로 응답을 만들 때만 쓴다.
 */
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

    @QueryProjection
    public DeliverySummaryResult {
    }


    public static DeliverySummaryResult from(Delivery delivery) {
        return new DeliverySummaryResult(delivery.getId(), delivery.getOrderId(), delivery.getStatus(),
                delivery.getSupplierCompanyId(), delivery.getReceiverCompanyId(),
                delivery.getDepartureHubId(), delivery.getDestinationHubId(),
                delivery.getPickupAddress(), delivery.getDeliveryAddress(),
                delivery.getRecipientName(), delivery.getRecipientSlackId(),
                delivery.getCreatedAt(), delivery.getUpdatedAt());
    }
}
