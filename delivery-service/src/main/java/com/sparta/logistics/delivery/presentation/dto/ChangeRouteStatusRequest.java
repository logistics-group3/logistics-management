package com.sparta.logistics.delivery.presentation.dto;

import com.sparta.logistics.delivery.application.dto.ChangeRouteStatusCommand;
import com.sparta.logistics.delivery.domain.RouteStatus;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * D-05 요청. status는 MOVING 또는 ARRIVED다. 실제 거리·시간은 허브 이동 도착에서만 필수라 Entity가 검사한다.
 * 거리 자릿수는 컬럼 DECIMAL(8,2)와 같다.
 */
public record ChangeRouteStatusRequest(
        @NotNull RouteStatus status,
        @PositiveOrZero @Digits(integer = 6, fraction = 2) BigDecimal actualDistanceKm,
        @PositiveOrZero Integer actualDurationMinutes
) {

    public ChangeRouteStatusCommand toCommand() {
        return new ChangeRouteStatusCommand(status, actualDistanceKm, actualDurationMinutes);
    }
}
