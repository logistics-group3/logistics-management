package com.sparta.logistics.delivery.domain;

import java.util.UUID;

/**
 * 배송담당자(값 객체). user 중 is_delivery_manager = true이고 소속 허브가 있는 사람이다(delivery.md 2-1).
 * user의 사용자 정보를 배송 용어로 바꿔 들여온 것이라 배송에 필요한 값만 둔다.
 */
public record DeliveryManager(UUID userId, UUID hubId) {
}
