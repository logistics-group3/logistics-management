package com.sparta.logistics.company.presentation.dto;

import com.sparta.logistics.company.application.dto.UpdateProductCommand;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** P-04 요청. 보낸 필드만 수정한다. 빈 문자열 처리는 UpdateCompanyRequest와 같다. */
public record UpdateProductRequest(
        @Pattern(regexp = ".*\\S.*", message = "상품명은 비워 둘 수 없습니다.")
        @Size(max = 100, message = "상품명은 100자 이하여야 합니다.")
        String name,

        String description,

        @PositiveOrZero(message = "가격은 0 이상이어야 합니다.")
        Long price
) {

    public UpdateProductCommand toCommand() {
        return new UpdateProductCommand(name, description, price);
    }
}
