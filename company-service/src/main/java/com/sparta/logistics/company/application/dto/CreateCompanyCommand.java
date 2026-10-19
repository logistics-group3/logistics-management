package com.sparta.logistics.company.application.dto;

import java.util.UUID;

/** C-01 입력. managerId는 MASTER면 필수, USER면 null이거나 본인 ID다. */
public record CreateCompanyCommand(
        String name,
        UUID hubId,
        String address,
        UUID managerId
) {
}
