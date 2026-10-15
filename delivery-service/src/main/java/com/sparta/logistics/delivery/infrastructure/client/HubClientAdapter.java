package com.sparta.logistics.delivery.infrastructure.client;

import com.sparta.logistics.delivery.application.HubRouteReader;
import com.sparta.logistics.delivery.domain.RouteInfo;
import com.sparta.logistics.delivery.infrastructure.client.dto.HubRouteResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 경로가 없으면 hub가 돌려준 4xx 코드(예: HUB_ROUTE_NOT_FOUND)가 그대로 올라가 주문이 실패한다. */
@Component
@RequiredArgsConstructor
public class HubClientAdapter implements HubRouteReader {

    private final HubClient hubClient;

    @Override
    public RouteInfo getExpectedRoute(UUID departureHubId, UUID arrivalHubId) {
        HubRouteResponse route = hubClient.getHubRoute(departureHubId, arrivalHubId);
        return new RouteInfo(route.distanceKm(), route.durationMinutes());
    }
}
