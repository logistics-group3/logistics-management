package com.sparta.logistics.order.presentation.dto;

import com.sparta.logistics.order.application.dto.OrderCreationProgress;
import com.sparta.logistics.order.domain.OrderCreationStatus;
import java.util.UUID;

/** O-07 주문 생성 진행 조회 응답. order는 COMPLETED일 때만, 실패 사유는 ABORTED·RECOVERY_REQUIRED일 때만 채운다. */
public record OrderCreationProgressResponse(UUID orderId, OrderCreationStatus status, String failureCode,
                                            String failureMessage, OrderResponse order) {

    public static OrderCreationProgressResponse from(OrderCreationProgress progress) {
        return new OrderCreationProgressResponse(progress.orderId(), progress.status(), progress.failureCode(),
                progress.failureMessage(), progress.order() == null ? null : OrderResponse.from(progress.order()));
    }
}
