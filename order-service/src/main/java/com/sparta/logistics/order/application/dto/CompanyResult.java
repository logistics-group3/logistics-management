package com.sparta.logistics.order.application.dto;

import java.util.UUID;

/** I-07에서 주문 생성에 필요한 업체 정보. 다른 서비스 DTO를 직접 공유하지 않는다. */
public record CompanyResult(UUID companyId, String name, UUID hubId, String address, UUID managerId) {
}
