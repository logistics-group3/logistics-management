package com.sparta.logistics.delivery.presentation.dto;

import com.sparta.logistics.delivery.application.dto.DeliveryResult;
import com.sparta.logistics.delivery.application.dto.DeliveryRouteResult;
import com.sparta.logistics.delivery.application.dto.DeliverySummaryResult;
import com.sparta.logistics.delivery.domain.DeliveryStatus;
import com.sparta.logistics.delivery.domain.RouteStatus;
import com.sparta.logistics.delivery.domain.RouteType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** D-02~D-05 응답: 배송과 구간(SA 6.8 DeliveryResponse). */
public record DeliveryResponse(
        UUID deliveryId,
        UUID orderId,
        DeliveryStatus status,
        UUID supplierCompanyId,
        UUID receiverCompanyId,
        UUID departureHubId,
        UUID destinationHubId,
        String pickupAddress,
        String deliveryAddress,
        String recipientName,
        String recipientSlackId,
        List<RouteResponse> routes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static DeliveryResponse from(DeliveryResult result) {
        DeliverySummaryResult d = result.delivery();
        return new DeliveryResponse(d.deliveryId(), d.orderId(), d.status(),
                d.supplierCompanyId(), d.receiverCompanyId(), d.departureHubId(), d.destinationHubId(),
                d.pickupAddress(), d.deliveryAddress(), d.recipientName(), d.recipientSlackId(),
                result.routes().stream().map(RouteResponse::from).toList(),
                d.createdAt(), d.updatedAt());
    }

    public record RouteResponse(
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

        static RouteResponse from(DeliveryRouteResult route) {
            return new RouteResponse(route.deliveryRouteId(), route.sequence(), route.routeType(),
                    route.departureHubId(), route.arrivalHubId(),
                    route.expectedDistanceKm(), route.expectedDurationMinutes(),
                    route.actualDistanceKm(), route.actualDurationMinutes(),
                    route.status(), route.managerId());
        }
    }
}
