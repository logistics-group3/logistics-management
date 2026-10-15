package com.sparta.logistics.delivery.application.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.sparta.logistics.delivery.domain.Delivery;
import java.util.List;

/**
 * D-02~D-05 응답(SA 6.8 DeliveryResponse). 배송(DeliverySummaryResult)과 구간(DeliveryRouteResult) 두 Result를 합친 조합이다.
 * delivery 코드 규칙: Result 하나는 그대로 응답하고, 여러 Result를 합쳐야 할 때만 이렇게 Response를 둔다(결정 로그 18).
 * 합치는 일이 서비스 안에서 일어나므로(D-02 권한 확인, D-03~D-05 Entity) application/dto에 둔다.
 * Note: @JsonUnwrapped가 delivery의 필드를 JSON 최상위로 펼쳐 {deliveryId, ..., routes} 모양이 된다.
 */
public record DeliveryResponse(@JsonUnwrapped DeliverySummaryResult delivery, List<DeliveryRouteResult> routes) {

    public static DeliveryResponse from(Delivery delivery) {
        return new DeliveryResponse(DeliverySummaryResult.from(delivery),
                delivery.getRoutes().stream().map(DeliveryRouteResult::from).toList());
    }
}
