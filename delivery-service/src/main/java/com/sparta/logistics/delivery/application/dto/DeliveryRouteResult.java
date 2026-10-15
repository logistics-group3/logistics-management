package com.sparta.logistics.delivery.application.dto;

import com.querydsl.core.annotations.QueryProjection;
import com.sparta.logistics.delivery.domain.DeliveryRoute;
import com.sparta.logistics.delivery.domain.RouteInfo;
import com.sparta.logistics.delivery.domain.RouteStatus;
import com.sparta.logistics.delivery.domain.RouteType;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public record DeliveryRouteResult(
        UUID deliveryRouteId,
        int sequence,
        RouteType routeType,
        UUID departureHubId,
        UUID arrivalHubId,
        BigDecimal expectedDistanceKm,
        Integer expectedDurationMinutes,
        BigDecimal actualDistanceKm,
        Integer actualDurationMinutes,
        RouteStatus status,
        UUID managerId
) {

    @QueryProjection
    public DeliveryRouteResult {
    }


    public static DeliveryRouteResult from(DeliveryRoute route) {
        Optional<RouteInfo> expected = Optional.ofNullable(route.getExpected());
        Optional<RouteInfo> actual = Optional.ofNullable(route.getActual());
        return new DeliveryRouteResult(route.getId(), route.getSequence(), route.getRouteType(),
                route.getDepartureHubId(), route.getArrivalHubId(),
                expected.map(RouteInfo::distanceKm).orElse(null),
                expected.map(RouteInfo::durationMinutes).orElse(null),
                actual.map(RouteInfo::distanceKm).orElse(null),
                actual.map(RouteInfo::durationMinutes).orElse(null),
                route.getStatus(), route.getManagerId());
    }
}
