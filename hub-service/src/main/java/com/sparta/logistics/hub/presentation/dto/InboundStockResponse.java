package com.sparta.logistics.hub.presentation.dto;

import com.sparta.logistics.hub.application.dto.StockResult;
import java.util.UUID;

/** H-06 응답(SA 6.5). quantity는 입고 후 재고 수량이다. */
public record InboundStockResponse(
        UUID hubStockId,
        UUID hubId,
        UUID productId,
        int quantity
) {

    public static InboundStockResponse from(StockResult result) {
        return new InboundStockResponse(result.hubStockId(), result.hubId(), result.productId(), result.quantity());
    }
}
