package com.sparta.logistics.order.application.dto;

import java.util.UUID;

/** I-08 상품 정보. 가격은 정수 원 단위다. */
public record ProductResult(UUID productId, UUID companyId, String name, long price) {
}
