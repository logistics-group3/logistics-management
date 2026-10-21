package com.sparta.logistics.delivery.application.assignment;

import java.util.UUID;

/** 배정 결과를 order에 알릴 배송(I-20). 배정이 끝났거나 수동 지정으로 넘긴 배송이다(결정 로그 29). */
public record AssignedDelivery(UUID deliveryId, UUID orderId) {
}
