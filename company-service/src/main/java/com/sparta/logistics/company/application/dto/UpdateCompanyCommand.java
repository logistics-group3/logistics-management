package com.sparta.logistics.company.application.dto;

import java.util.UUID;

/** C-04 입력. null인 값은 바꾸지 않는다. managerId 변경은 MASTER만 한다. */
public record UpdateCompanyCommand(
        String name,
        UUID hubId,
        String address,
        UUID managerId
) {
}
