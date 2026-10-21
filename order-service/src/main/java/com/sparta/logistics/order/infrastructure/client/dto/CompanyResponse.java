package com.sparta.logistics.order.infrastructure.client.dto;

import java.util.UUID;

/** I-07 전송 응답. company-service의 클래스를 직접 공유하지 않는다. */
public record CompanyResponse(UUID companyId, String name, UUID hubId, String address, UUID managerId) {
}
