package com.sparta.logistics.hub.presentation.dto;

import com.sparta.logistics.hub.application.dto.AllocateStockCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/** I-05 요청. supplierHubId는 공급업체 허브이며 배송 출발 허브와 같다(ADR 0003, SA 6.3과 다름). */
public record AllocateStockRequest(
        @NotNull(message = "주문 ID는 필수입니다.")
        UUID orderId,

        @NotNull(message = "상품 ID는 필수입니다.")
        UUID productId,

        @NotNull(message = "수량은 필수입니다.")
        @Positive(message = "수량은 1 이상이어야 합니다.")
        Integer quantity,

        @NotNull(message = "공급업체 허브 ID는 필수입니다.")
        UUID supplierHubId
) {

    public AllocateStockCommand toCommand() {
        return new AllocateStockCommand(orderId, productId, quantity, supplierHubId);
    }
}
