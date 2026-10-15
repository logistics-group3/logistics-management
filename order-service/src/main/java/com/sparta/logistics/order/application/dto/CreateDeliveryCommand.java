package com.sparta.logistics.order.application.dto;

import java.util.UUID;

/** I-09 배송 생성에 전달할 정보(SA 6.3). 수령인 기본값은 요청업체명이다(SA 5.6). */
public record CreateDeliveryCommand(UUID orderId, UUID supplierCompanyId, UUID receiverCompanyId,
                                    UUID departureHubId, UUID destinationHubId, String pickupAddress,
                                    String deliveryAddress, String recipientName, UUID receiverManagerId) {
}
