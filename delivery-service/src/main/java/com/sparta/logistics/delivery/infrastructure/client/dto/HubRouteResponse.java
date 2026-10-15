package com.sparta.logistics.delivery.infrastructure.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** hub I-03 응답(SA 6.3). */
public record HubRouteResponse(UUID hubRouteId, BigDecimal distanceKm, Integer durationMinutes) {
}
