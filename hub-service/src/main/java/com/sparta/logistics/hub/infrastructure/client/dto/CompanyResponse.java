package com.sparta.logistics.hub.infrastructure.client.dto;

import java.util.UUID;

/** company-service I-07 응답에서 hub가 쓰는 필드만 가져온다. 입고 권한 확인용이다. */
public record CompanyResponse(UUID companyId, UUID hubId, UUID managerId) {
}
