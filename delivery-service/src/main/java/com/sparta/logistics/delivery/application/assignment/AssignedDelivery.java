package com.sparta.logistics.delivery.application.assignment;

import java.util.UUID;

/** 배정이 끝난 배송. 담당자 알림(order)에 쓴다. */
public record AssignedDelivery(UUID deliveryId, UUID orderId) {
}
