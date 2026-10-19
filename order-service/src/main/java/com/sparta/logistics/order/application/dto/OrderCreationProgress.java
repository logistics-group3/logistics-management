package com.sparta.logistics.order.application.dto;

import com.sparta.logistics.order.domain.OrderCreationStatus;
import java.util.UUID;

/**
 * 주문 생성 진행 조회(O-07) 결과. order는 COMPLETED일 때만 채우고, 실패 사유는 ABORTED·RECOVERY_REQUIRED에서 채운다.
 */
public record OrderCreationProgress(UUID orderId, OrderCreationStatus status, String failureCode, String failureMessage,
                                    OrderResult order) {
}
