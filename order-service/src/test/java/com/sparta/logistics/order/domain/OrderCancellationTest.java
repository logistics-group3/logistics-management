package com.sparta.logistics.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.logistics.common.exception.CommonErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderCancellationTest {

    @Test
    @DisplayName("주문 취소는 ORDERED에서 CANCELED로만 바뀌고 주문 스냅샷을 유지한다")
    void cancelsOrderedOrder() {
        Order order = order();
        UUID deliveryId = order.getDeliveryId();

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getDeliveryId()).isEqualTo(deliveryId);
        assertThat(order.getRequestNote()).isEqualTo("오전 도착 희망");
        assertThat(order.getProductName()).isEqualTo("주문 당시 상품");
        assertThat(order.getQuantity()).isEqualTo(50);
        assertThat(order.getTotalPrice()).isEqualTo(600_000L);
    }

    @Test
    @DisplayName("이미 취소된 주문은 다시 취소할 수 없다")
    void rejectsRepeatedCancellation() {
        Order order = order();
        order.cancel();

        assertThatThrownBy(order::cancel)
                .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_STATUS_TRANSITION);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    @DisplayName("취소된 주문은 요청사항을 수정할 수 없다")
    void canceledOrderRejectsNoteUpdate() {
        Order order = order();
        order.cancel();

        assertThatThrownBy(() -> order.updateRequestNote("변경 요청"))
                .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_STATUS_TRANSITION);
    }

    private Order order() {
        return Order.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "주문 당시 상품", 12_000L, 50, UUID.randomUUID(), "오전 도착 희망");
    }
}
