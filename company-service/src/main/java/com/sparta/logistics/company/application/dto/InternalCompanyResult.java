package com.sparta.logistics.company.application.dto;

import com.sparta.logistics.company.domain.Company;
import java.util.UUID;

/** I-07 응답. order가 주문 생성 때 공급·수령 업체의 허브, 주소, 관리자를 확인한다(SA 6.3). */
public record InternalCompanyResult(
        UUID companyId,
        String name,
        UUID hubId,
        String address,
        UUID managerId
) {

    public static InternalCompanyResult from(Company company) {
        return new InternalCompanyResult(company.getId(), company.getName(), company.getHubId(),
                company.getAddress(), company.getManagerId());
    }
}
