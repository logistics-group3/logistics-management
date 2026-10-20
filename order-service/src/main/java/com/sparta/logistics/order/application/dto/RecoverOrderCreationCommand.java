package com.sparta.logistics.order.application.dto;

import com.sparta.logistics.order.domain.OrderCreationStatus;
import java.util.UUID;

public record RecoverOrderCreationCommand(UUID recoveryId, UUID attemptId, OrderCreationStatus expectedStatus,
        String reason, boolean originalExecutionStopped, UUID resumeExecutionId) {
}
