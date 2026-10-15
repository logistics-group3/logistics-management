package com.sparta.logistics.delivery.application.dto;

import com.sparta.logistics.delivery.domain.Delivery;

/**
 * I-09 결과.
 *
 * @param created 이번 요청으로 만들었으면 true(201), 같은 orderId 배송이 이미 있었으면 false(200)
 */
public record CreateDeliveryResult(DeliveryStatusResult delivery, boolean created) {

    public static CreateDeliveryResult created(Delivery delivery) {
        return new CreateDeliveryResult(DeliveryStatusResult.from(delivery), true);
    }

    public static CreateDeliveryResult existing(Delivery delivery) {
        return new CreateDeliveryResult(DeliveryStatusResult.from(delivery), false);
    }
}
