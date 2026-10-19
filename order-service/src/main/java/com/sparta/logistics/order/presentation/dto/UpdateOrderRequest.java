package com.sparta.logistics.order.presentation.dto;

import com.sparta.logistics.order.application.dto.UpdateOrderCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateOrderRequest(
        @NotBlank(message = "요청사항은 필수입니다.")
        @Size(max = 500, message = "요청사항은 500자 이내여야 합니다.") String requestNote) {

    public UpdateOrderCommand toCommand() {
        return new UpdateOrderCommand(requestNote);
    }
}
