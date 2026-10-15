package com.sparta.logistics.company.presentation.dto;

import com.sparta.logistics.company.application.dto.InternalCompanyResult;
import java.util.UUID;

/** I-07 응답(SA 6.3). 호출자는 order다. */
public record InternalCompanyResponse(
        UUID companyId,
        String name,
        UUID hubId,
        String address,
        UUID managerId
) {

    public static InternalCompanyResponse from(InternalCompanyResult result) {
        return new InternalCompanyResponse(result.companyId(), result.name(), result.hubId(), result.address(),
                result.managerId());
    }
}
