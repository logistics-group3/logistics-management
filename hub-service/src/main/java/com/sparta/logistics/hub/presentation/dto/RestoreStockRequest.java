package com.sparta.logistics.hub.presentation.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** I-06 요청(SA 6.3). */
public record RestoreStockRequest(
        @NotNull(message = "주문 ID는 필수입니다.")
        UUID orderId
) {
}
