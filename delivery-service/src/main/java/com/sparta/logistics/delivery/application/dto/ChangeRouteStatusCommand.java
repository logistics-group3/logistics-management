package com.sparta.logistics.delivery.application.dto;

import com.sparta.logistics.delivery.domain.RouteInfo;
import com.sparta.logistics.delivery.domain.RouteStatus;
import java.math.BigDecimal;

/** D-05 입력. 실제 거리·시간은 허브 이동 도착에서만 필수다. */
public record ChangeRouteStatusCommand(RouteStatus status, BigDecimal actualDistanceKm, Integer actualDurationMinutes) {

    public RouteInfo actual() {
        return new RouteInfo(actualDistanceKm, actualDurationMinutes);
    }
}
