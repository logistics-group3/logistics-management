package com.sparta.logistics.order.presentation.dto;

import com.sparta.logistics.order.application.notification.AssignmentNotification;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** I-20 요청 본문 */
public record AssignmentNotificationRequest(@NotNull UUID deliveryId, @NotNull AssignmentNotification.Type type) {

    public AssignmentNotification toNotification(UUID orderId) {
        return new AssignmentNotification(orderId, deliveryId, type);
    }
}
