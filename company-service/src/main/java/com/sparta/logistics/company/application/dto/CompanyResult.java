package com.sparta.logistics.company.application.dto;

import com.sparta.logistics.company.domain.Company;
import java.time.LocalDateTime;
import java.util.UUID;

/** C-01~C-04 출력(SA 6.6 CompanyResponse) */
public record CompanyResult(
        UUID companyId,
        String name,
        UUID hubId,
        String address,
        UUID managerId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static CompanyResult from(Company company) {
        return new CompanyResult(company.getId(), company.getName(), company.getHubId(), company.getAddress(),
                company.getManagerId(), company.getCreatedAt(), company.getUpdatedAt());
    }
}
