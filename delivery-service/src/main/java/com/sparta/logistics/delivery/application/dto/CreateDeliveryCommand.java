package com.sparta.logistics.delivery.application.dto;

import java.util.UUID;

/** I-09 배송 생성 입력. 업체 허브·주소·관리자는 order가 company에서 조회해 넘긴다(SA 7.7). */
public record CreateDeliveryCommand(
        UUID orderId,
        UUID supplierCompanyId,
        UUID receiverCompanyId,
        UUID departureHubId,
        UUID destinationHubId,
        String pickupAddress,
        String deliveryAddress,
        String recipientName,
        UUID receiverManagerId
) {
}
