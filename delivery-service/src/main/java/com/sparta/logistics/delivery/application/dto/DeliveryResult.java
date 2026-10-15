package com.sparta.logistics.delivery.application.dto;

import com.sparta.logistics.delivery.domain.Delivery;
import java.util.List;

/** D-02~D-05 결과. 배송과 구간 전체(SA 6.8 DeliveryResponse). */
public record DeliveryResult(DeliverySummaryResult delivery, List<DeliveryRouteResult> routes) {

    public static DeliveryResult from(Delivery delivery) {
        return new DeliveryResult(DeliverySummaryResult.from(delivery),
                delivery.getRoutes().stream().map(DeliveryRouteResult::from).toList());
    }
}
