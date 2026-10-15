package com.sparta.logistics.company.presentation.dto;

import com.sparta.logistics.company.domain.CompanySearchCondition;
import java.util.UUID;

/** C-02 검색 쿼리 파라미터. page, size, sort는 Pageable로 따로 받는다. */
public record CompanySearchRequest(
        String name,
        UUID hubId,
        UUID managerId
) {

    public CompanySearchCondition toCondition() {
        return new CompanySearchCondition(name, hubId, managerId);
    }
}
