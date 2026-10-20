package com.sparta.logistics.hub.presentation.dto;

import com.sparta.logistics.hub.application.dto.InboundStockCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/** H-06 요청. 입고 수량은 1 이상이다(SA 6.5). */
public record InboundStockRequest(
        @NotNull(message = "상품 ID는 필수입니다.")
        UUID productId,

        @NotNull(message = "입고 수량은 필수입니다.")
        @Positive(message = "입고 수량은 1 이상이어야 합니다.")
        Integer quantity
) {

    public InboundStockCommand toCommand(UUID hubId) {
        return new InboundStockCommand(hubId, productId, quantity);
    }
}
