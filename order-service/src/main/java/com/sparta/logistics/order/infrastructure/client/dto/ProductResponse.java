package com.sparta.logistics.order.infrastructure.client.dto;

import java.util.UUID;

/** I-08 전송 응답. price는 필드 누락을 판별하기 위해 Long으로 받는다. */
public record ProductResponse(UUID productId, UUID companyId, String name, Long price) {
}
