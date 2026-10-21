package com.sparta.logistics.company.presentation.dto;

import com.sparta.logistics.company.domain.ProductSearchCondition;
import java.util.UUID;

/** P-02 검색 쿼리 파라미터. hubId는 소속 업체의 관리 허브다. */
public record ProductSearchRequest(
        String name,
        UUID companyId,
        UUID hubId
) {

    public ProductSearchCondition toCondition() {
        return new ProductSearchCondition(name, companyId, hubId);
    }
}
