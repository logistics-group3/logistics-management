package com.sparta.logistics.delivery.application.dto;

import com.sparta.logistics.delivery.domain.DeliveryStatus;
import java.util.List;
import java.util.UUID;

/** I-15 전용 조회 결과. 처리할 구간의 담당자 ID만 반환하며 이메일은 user가 소유한다. */
public record DeliveryNotificationRecipientsResult(UUID deliveryId, UUID orderId, DeliveryStatus status,
                                                   List<UUID> managerIds) {
}
