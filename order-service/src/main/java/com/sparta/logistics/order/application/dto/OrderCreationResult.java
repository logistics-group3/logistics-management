package com.sparta.logistics.order.application.dto;

import com.sparta.logistics.order.domain.OrderStatus;
import java.util.UUID;

/**
 * I-21 주문 생성 결과.
 *
 * @param result      생성 시도 상태(PROCESSING, COMPLETED, ABORTED, RECOVERY_REQUIRED). 기록이 없으면 NONE
 * @param orderStatus 주문 상태. 주문이 없거나 삭제됐으면 null
 */
public record OrderCreationResult(UUID orderId, String result, OrderStatus orderStatus) {

    public static final String NONE = "NONE";
}
