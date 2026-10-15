package com.sparta.logistics.delivery.presentation.dto;

import com.sparta.logistics.delivery.application.dto.DeliveryStatusResult;
import com.sparta.logistics.delivery.domain.DeliveryStatus;
import java.util.UUID;

/** 내부 API(I-09~I-12) 응답. ApiResponse로 감싸지 않는다(ADR 0010). */
public record DeliveryStatusResponse(UUID deliveryId, UUID orderId, DeliveryStatus status) {

    public static DeliveryStatusResponse from(DeliveryStatusResult result) {
        return new DeliveryStatusResponse(result.deliveryId(), result.orderId(), result.status());
    }
}
