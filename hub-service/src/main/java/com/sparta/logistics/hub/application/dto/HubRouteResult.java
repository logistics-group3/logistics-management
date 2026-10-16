package com.sparta.logistics.hub.application.dto;

import com.sparta.logistics.hub.domain.Hub;
import com.sparta.logistics.hub.domain.HubRoute;
import java.math.BigDecimal;
import java.util.UUID;

/** H-03, H-04 조회 결과. 출발·도착 허브는 ID와 이름만 담는다. */
public record HubRouteResult(
        UUID hubRouteId,
        HubSummary departureHub,
        HubSummary arrivalHub,
        BigDecimal distanceKm,
        int durationMinutes
) {

    public static HubRouteResult from(HubRoute route) {
        return new HubRouteResult(
                route.getId(),
                HubSummary.from(route.getDepartureHub()),
                HubSummary.from(route.getArrivalHub()),
                route.getDistanceKm(),
                route.getDurationMinutes());
    }

    public record HubSummary(UUID hubId, String name) {

        static HubSummary from(Hub hub) {
            return new HubSummary(hub.getId(), hub.getName());
        }
    }
}
