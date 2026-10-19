package com.sparta.logistics.order.application.notification;

import java.util.List;
import java.util.UUID;

/** 배송 상태와 담당자의 연락처를 서비스 경계를 통해 읽는다. */
public interface NotificationDirectory {
    Targets findTargets(UUID deliveryId);
    Contact findContact(UUID userId);

    record Targets(UUID deliveryId, UUID orderId, String status, List<UUID> managerIds) { }
    record Contact(UUID userId, boolean isDeliveryManager, String slackEmail) { }
}
