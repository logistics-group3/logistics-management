package com.sparta.logistics.hub.presentation.dto;

import com.sparta.logistics.hub.application.dto.StockResult;
import java.time.LocalDateTime;
import java.util.UUID;

/** H-05 응답(SA 6.5 HubStockResponse). 상품명은 company 소유 데이터라 포함하지 않는다. */
public record HubStockResponse(
        UUID hubStockId,
        UUID hubId,
        UUID productId,
        int quantity,
        LocalDateTime updatedAt
) {

    public static HubStockResponse from(StockResult result) {
        return new HubStockResponse(result.hubStockId(), result.hubId(), result.productId(), result.quantity(), result.updatedAt());
    }
}
