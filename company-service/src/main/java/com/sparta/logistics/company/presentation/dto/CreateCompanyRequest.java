package com.sparta.logistics.company.presentation.dto;

import com.sparta.logistics.company.application.dto.CreateCompanyCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** C-01 요청. managerId는 MASTER면 필수, USER면 생략하거나 본인 ID다(application에서 검사). */
public record CreateCompanyRequest(
        @NotBlank(message = "업체명은 필수입니다.")
        @Size(max = 100, message = "업체명은 100자 이하여야 합니다.")
        String name,

        @NotNull(message = "관리 허브 ID는 필수입니다.")
        UUID hubId,

        @NotBlank(message = "업체 주소는 필수입니다.")
        @Size(max = 255, message = "업체 주소는 255자 이하여야 합니다.")
        String address,

        UUID managerId
) {

    public CreateCompanyCommand toCommand() {
        return new CreateCompanyCommand(name, hubId, address, managerId);
    }
}
