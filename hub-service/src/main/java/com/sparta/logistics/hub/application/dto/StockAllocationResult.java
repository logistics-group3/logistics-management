package com.sparta.logistics.hub.application.dto;

import java.util.UUID;

/** I-05 결과. 공급업체 허브 한 곳에서 차감한 수량이다(ADR 0003). 같은 주문의 재요청이면 처음 차감한 값이다. */
public record StockAllocationResult(UUID hubId, int quantity) {
}
