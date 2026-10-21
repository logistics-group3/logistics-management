package com.sparta.logistics.hub.presentation.dto;

import com.sparta.logistics.hub.application.dto.HubRouteResult;
import java.math.BigDecimal;
import java.util.UUID;

/** H-03, H-04 응답(SA 6.5 HubRouteResponse). 출발·도착 허브는 ID와 이름만 담는다. */
public record HubRouteResponse(
        UUID hubRouteId,
        HubSummary departureHub,
        HubSummary arrivalHub,
        BigDecimal distanceKm,
        int durationMinutes
) {

    public static HubRouteResponse from(HubRouteResult result) {
        return new HubRouteResponse(
                result.hubRouteId(),
                HubSummary.from(result.departureHub()),
                HubSummary.from(result.arrivalHub()),
                result.distanceKm(),
                result.durationMinutes());
    }

    public record HubSummary(UUID hubId, String name) {

        static HubSummary from(HubRouteResult.HubSummary summary) {
            return new HubSummary(summary.hubId(), summary.name());
        }
    }
}
