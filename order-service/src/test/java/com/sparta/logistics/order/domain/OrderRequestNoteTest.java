package com.sparta.logistics.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.logistics.common.exception.CommonErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class OrderRequestNoteTest {

    @Test
    @DisplayName("요청사항은 500자까지 수정하며 상품·수량·금액·배송·상태를 유지한다")
    void changesOnlyRequestNote() {
        Order order = order();
        UUID productId = order.getProductId();
        UUID deliveryId = order.getDeliveryId();

        order.updateRequestNote("가".repeat(500));

        assertThat(order.getRequestNote()).hasSize(500);
        assertThat(order.getProductId()).isEqualTo(productId);
        assertThat(order.getDeliveryId()).isEqualTo(deliveryId);
        assertThat(order.getProductName()).isEqualTo("주문 당시 상품");
        assertThat(order.getUnitPrice()).isEqualTo(12_000L);
        assertThat(order.getQuantity()).isEqualTo(50);
        assertThat(order.getTotalPrice()).isEqualTo(600_000L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ORDERED);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    @DisplayName("수정 요청사항이 null·빈 문자열·공백이면 기존 값을 유지하고 거부한다")
    void rejectsBlankNote(String note) {
        Order order = order();

        assertThatThrownBy(() -> order.updateRequestNote(note))
                .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_REQUEST);
        assertThat(order.getRequestNote()).isEqualTo("기존 요청사항");
    }

    @Test
    @DisplayName("501자 요청사항은 기존 값을 유지하고 거부한다")
    void rejectsLongNote() {
        Order order = order();

        assertThatThrownBy(() -> order.updateRequestNote("가".repeat(501)))
                .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_REQUEST);
        assertThat(order.getRequestNote()).isEqualTo("기존 요청사항");
    }

    @Test
    @DisplayName("취소된 주문은 요청사항을 수정할 수 없다")
    void rejectsCanceledOrder() {
        Order order = order();
        ReflectionTestUtils.setField(order, "status", OrderStatus.CANCELED);

        assertThatThrownBy(() -> order.updateRequestNote("변경 요청"))
                .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_STATUS_TRANSITION);
        assertThat(order.getRequestNote()).isEqualTo("기존 요청사항");
    }

    private Order order() {
        return Order.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "주문 당시 상품", 12_000L, 50, UUID.randomUUID(), "기존 요청사항");
    }
}
