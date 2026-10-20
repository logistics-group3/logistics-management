package com.sparta.logistics.order.presentation.dto;

import com.sparta.logistics.order.application.dto.RecoverOrderCreationCommand;
import com.sparta.logistics.order.domain.OrderCreationStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record RecoverOrderCreationRequest(@NotNull UUID recoveryId, @NotNull UUID attemptId,
        @NotNull OrderCreationStatus expectedStatus, @NotBlank @Size(max = 500) String reason,
        @AssertTrue(message = "원래 작업과 이전 복구 실행이 중단됐는지 먼저 확인해야 합니다.") boolean originalExecutionStopped,
        UUID resumeExecutionId) {
    public RecoverOrderCreationCommand toCommand() {
        return new RecoverOrderCreationCommand(recoveryId, attemptId, expectedStatus, reason,
                originalExecutionStopped, resumeExecutionId);
    }
}
