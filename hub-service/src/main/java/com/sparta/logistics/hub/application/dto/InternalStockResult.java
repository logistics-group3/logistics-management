package com.sparta.logistics.hub.application.dto;

import java.util.UUID;

/** I-04 결과. company가 상품 생성 시 받는 재고 식별자와 수량이다. */
public record InternalStockResult(UUID hubStockId, int quantity) {

    public static InternalStockResult from(StockResult stock) {
        return new InternalStockResult(stock.hubStockId(), stock.quantity());
    }
}
