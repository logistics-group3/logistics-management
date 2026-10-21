package com.sparta.logistics.company.presentation.dto;

import com.sparta.logistics.company.application.dto.CreateProductCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** P-01 요청 */
public record CreateProductRequest(
        @NotBlank(message = "상품명은 필수입니다.")
        @Size(max = 100, message = "상품명은 100자 이하여야 합니다.")
        String name,

        String description,

        @NotNull(message = "가격은 필수입니다.")
        @PositiveOrZero(message = "가격은 0 이상이어야 합니다.")
        Long price
) {

    public CreateProductCommand toCommand() {
        return new CreateProductCommand(name, description, price);
    }
}
