package com.sparta.logistics.hub.application;

import java.util.UUID;

/** company-service 업체 조회 포트(SA 6.3 I-07). 구현은 infrastructure/client가 맡는다. */
public interface CompanyReader {

    /** 입고 권한 판단에 필요한 업체 정보. 업체가 없으면 BusinessException(COMPANY_NOT_FOUND 404), 상대 장애는 503이다. */
    CompanyInfo getCompany(UUID companyId);

    record CompanyInfo(UUID hubId, UUID managerId) {
    }
}
