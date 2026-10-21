package com.sparta.logistics.hub.presentation.dto;

import com.sparta.logistics.hub.application.dto.InitializeStockCommand;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** I-04 요청(SA 6.3). */
public record InitializeStockRequest(
        @NotNull(message = "허브 ID는 필수입니다.")
        UUID hubId,

        @NotNull(message = "상품 ID는 필수입니다.")
        UUID productId
) {

    public InitializeStockCommand toCommand() {
        return new InitializeStockCommand(hubId, productId);
    }
}
