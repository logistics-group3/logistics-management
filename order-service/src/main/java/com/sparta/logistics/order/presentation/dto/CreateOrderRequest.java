package com.sparta.logistics.order.presentation.dto;

import com.sparta.logistics.order.application.dto.CreateOrderCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** O-01 요청(SA 6.7, 8.5). 주문자 ID와 가격을 클라이언트 입력으로 받지 않는다. */
public record CreateOrderRequest(
        @NotNull(message = "공급업체 ID는 필수입니다.") UUID supplierCompanyId,
        @NotNull(message = "수령업체 ID는 필수입니다.") UUID receiverCompanyId,
        @NotNull(message = "상품 ID는 필수입니다.") UUID productId,
        @NotNull(message = "주문 수량은 필수입니다.") @Positive(message = "주문 수량은 1 이상이어야 합니다.") Integer quantity,
        @Size(max = 500, message = "요청사항은 500자 이내여야 합니다.") String requestNote
) {

    public CreateOrderCommand toCommand() {
        return new CreateOrderCommand(supplierCompanyId, receiverCompanyId, productId, quantity, requestNote);
    }
}
