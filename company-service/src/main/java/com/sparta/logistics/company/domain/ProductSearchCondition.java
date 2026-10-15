package com.sparta.logistics.company.domain;

import java.util.UUID;

/**
 * P-02 상품 검색 조건. null인 조건은 적용하지 않는다. hubId는 소속 업체의 관리 허브로 거른다.
 */
public record ProductSearchCondition(
        String name,
        UUID companyId,
        UUID hubId
) {
}
