package com.sparta.logistics.delivery.application.dto;

import com.sparta.logistics.delivery.domain.Delivery;
import com.sparta.logistics.delivery.domain.DeliveryStatus;
import java.util.UUID;

/** 내부 API(I-09~I-12) 결과. order가 Saga 분기에 쓰는 값만 둔다. */
public record DeliveryStatusResult(UUID deliveryId, UUID orderId, DeliveryStatus status) {

    public static DeliveryStatusResult from(Delivery delivery) {
        return new DeliveryStatusResult(delivery.getId(), delivery.getOrderId(), delivery.getStatus());
    }
}
