package com.sparta.logistics.hub.application.dto;

import java.util.UUID;

/** supplierHubId는 공급업체 허브이며 배송 출발 허브(delivery I-09의 departureHubId)와 같다(ADR 0003). */
public record AllocateStockCommand(UUID orderId, UUID productId, int quantity, UUID supplierHubId) {
}
