package com.sparta.logistics.order.application.dto;

import java.util.UUID;

public record OrderCreationAttempt(UUID attemptId, UUID orderId, UUID ordererId) {
}
