package com.sparta.logistics.company.domain;

import java.util.UUID;

/**
 * C-02 업체 검색 조건. null인 조건은 적용하지 않는다.
 * Note: Repository 인터페이스(domain)가 받는 타입이라 application/dto가 아닌 domain에 둔다. domain은 다른 계층을 모른다.
 */
public record CompanySearchCondition(
        String name,
        UUID hubId,
        UUID managerId
) {
}
