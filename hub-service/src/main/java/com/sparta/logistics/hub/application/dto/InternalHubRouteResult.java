package com.sparta.logistics.hub.application.dto;

import com.sparta.logistics.hub.domain.HubRoute;
import java.math.BigDecimal;
import java.util.UUID;

/** I-03 조회 결과. delivery가 배송 생성 때 허브 이동 구간의 예상 거리·시간을 채우는 데 쓴다. */
public record InternalHubRouteResult(
        UUID hubRouteId,
        BigDecimal distanceKm,
        int durationMinutes
) {

    public static InternalHubRouteResult from(HubRoute route) {
        return new InternalHubRouteResult(route.getId(), route.getDistanceKm(), route.getDurationMinutes());
    }
}
