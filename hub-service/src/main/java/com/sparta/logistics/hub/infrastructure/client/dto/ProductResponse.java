package com.sparta.logistics.hub.infrastructure.client.dto;

import java.util.UUID;

/** company-service I-08 응답에서 hub가 쓰는 필드만 가져온다. 존재 확인과 입고 권한 확인(소속 업체)에 쓴다. */
public record ProductResponse(UUID productId, UUID companyId) {
}
