package com.sparta.logistics.delivery.infrastructure.client.dto;

import java.util.UUID;

/** I-20 요청. type은 ASSIGNED(배정 완료) 또는 MANUAL_REQUIRED(수동 지정 필요)다. */
public record AssignmentNotificationRequest(UUID deliveryId, String type) {

    public static final String ASSIGNED = "ASSIGNED";
    public static final String MANUAL_REQUIRED = "MANUAL_REQUIRED";
}
