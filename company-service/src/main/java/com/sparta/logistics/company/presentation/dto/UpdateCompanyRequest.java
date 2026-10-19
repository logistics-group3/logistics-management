package com.sparta.logistics.company.presentation.dto;

import com.sparta.logistics.company.application.dto.UpdateCompanyCommand;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * C-04 요청. 보낸 필드만 수정한다.
 * Note: 생략(null)은 허용하고 빈 문자열만 막으려고 @NotBlank 대신 @Pattern을 쓴다. Bean Validation은 null을 @Pattern 검사에서 통과시킨다.
 */
public record UpdateCompanyRequest(
        @Pattern(regexp = ".*\\S.*", message = "업체명은 비워 둘 수 없습니다.")
        @Size(max = 100, message = "업체명은 100자 이하여야 합니다.")
        String name,

        UUID hubId,

        @Pattern(regexp = ".*\\S.*", message = "업체 주소는 비워 둘 수 없습니다.")
        @Size(max = 255, message = "업체 주소는 255자 이하여야 합니다.")
        String address,

        UUID managerId
) {

    public UpdateCompanyCommand toCommand() {
        return new UpdateCompanyCommand(name, hubId, address, managerId);
    }
}
