package com.sparta.logistics.hub.application.dto;

import com.sparta.logistics.hub.domain.HubStock;
import java.time.LocalDateTime;
import java.util.UUID;

/** 재고 한 건의 조회·입고·초기화 결과(H-05, H-06, I-04). */
public record StockResult(
        UUID hubStockId,
        UUID hubId,
        UUID productId,
        int quantity,
        LocalDateTime updatedAt
) {

    public static StockResult from(HubStock stock) {
        return new StockResult(stock.getId(), stock.getHubId(), stock.getProductId(), stock.getQuantity(), stock.getUpdatedAt());
    }
}
