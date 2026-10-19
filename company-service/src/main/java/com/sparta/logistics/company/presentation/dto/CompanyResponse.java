package com.sparta.logistics.company.presentation.dto;

import com.sparta.logistics.company.application.dto.CompanyResult;
import java.time.LocalDateTime;
import java.util.UUID;

/** SA 6.6 CompanyResponse */
public record CompanyResponse(
        UUID companyId,
        String name,
        UUID hubId,
        String address,
        UUID managerId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static CompanyResponse from(CompanyResult result) {
        return new CompanyResponse(result.companyId(), result.name(), result.hubId(), result.address(),
                result.managerId(), result.createdAt(), result.updatedAt());
    }
}
