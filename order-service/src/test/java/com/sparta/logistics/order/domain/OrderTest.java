package com.sparta.logistics.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class OrderTest {

    @Test
    @DisplayName("주문은 선발급 ID와 상품 스냅샷을 보관하고 단가와 수량으로 금액을 계산한다")
    void createsOrderWithSnapshotAndPreallocatedId() {
        UUID orderId = UUID.randomUUID();
        UUID ordererId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();

        Order order = Order.create(orderId, ordererId, supplierId, receiverId, productId,
                "마른오징어", 12_000L, 50, deliveryId, "오전 도착 희망");

        assertThat(order.getId()).isEqualTo(orderId);
        assertThat(order.getOrdererId()).isEqualTo(ordererId);
        assertThat(order.getSupplierCompanyId()).isEqualTo(supplierId);
        assertThat(order.getReceiverCompanyId()).isEqualTo(receiverId);
        assertThat(order.getProductId()).isEqualTo(productId);
        assertThat(order.getProductName()).isEqualTo("마른오징어");
        assertThat(order.getUnitPrice()).isEqualTo(12_000L);
        assertThat(order.getQuantity()).isEqualTo(50);
        assertThat(order.getTotalPrice()).isEqualTo(600_000L);
        assertThat(order.getDeliveryId()).isEqualTo(deliveryId);
        assertThat(order.getRequestNote()).isEqualTo("오전 도착 희망");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ORDERED);
    }

    @Test
    @DisplayName("가격이 0원이고 요청사항이 없어도 주문을 만들 수 있다")
    void allowsZeroPriceAndNoRequestNote() {
        Order order = create(0L, 1, "상품", null);

        assertThat(order.getTotalPrice()).isZero();
        assertThat(order.getRequestNote()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    @DisplayName("수량이 1 미만이면 주문을 거부한다")
    void rejectsNonPositiveQuantity(int quantity) {
        assertThatThrownBy(() -> create(1_000L, quantity, "상품", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1L, Long.MIN_VALUE})
    @DisplayName("단가가 음수이면 주문을 거부한다")
    void rejectsNegativeUnitPrice(long unitPrice) {
        assertThatThrownBy(() -> create(unitPrice, 1, "상품", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("주문 금액이 Long 범위 안이면 최댓값도 정확하게 보관한다")
    void allowsLargestRepresentableTotal() {
        assertThat(create(Long.MAX_VALUE, 1, "상품", null).getTotalPrice())
                .isEqualTo(Long.MAX_VALUE);
    }

    @ParameterizedTest
    @CsvSource({"9223372036854775807, 2", "9223372036854775807, 3"})
    @DisplayName("곱셈 결과가 Long 범위를 넘으면 음수나 잘린 양수 금액을 저장하지 않는다")
    void rejectsTotalPriceOverflow(long unitPrice, int quantity) {
        assertThatThrownBy(() -> create(unitPrice, quantity, "상품", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("공급업체와 수령업체가 같으면 주문을 거부한다")
    void rejectsSameSupplierAndReceiver() {
        UUID companyId = UUID.randomUUID();

        assertThatThrownBy(() -> Order.create(UUID.randomUUID(), UUID.randomUUID(), companyId,
                companyId, UUID.randomUUID(), "상품", 1_000L, 1, UUID.randomUUID(), null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_REQUEST);
    }

    @ParameterizedTest(name = "{0}가 없으면 주문을 거부한다")
    @CsvSource({"orderId, 0", "ordererId, 1", "supplierCompanyId, 2",
            "receiverCompanyId, 3", "productId, 4", "deliveryId, 5"})
    @DisplayName("주문과 외부 리소스의 필수 ID가 없으면 주문을 거부한다")
    void rejectsMissingRequiredId(String field, int index) {
        UUID[] ids = {UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
        ids[index] = null;

        assertThatThrownBy(() -> Order.create(ids[0], ids[1], ids[2], ids[3], ids[4],
                "상품", 1_000L, 1, ids[5], null))
                .as(field)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_REQUEST);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @DisplayName("상품명이 없거나 공백뿐이면 주문을 거부한다")
    void rejectsBlankProductName(String productName) {
        assertThatThrownBy(() -> create(1_000L, 1, productName, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("상품명 100자와 요청사항 500자는 잘라내지 않고 보관한다")
    void allowsMaximumTextLengths() {
        Order order = create(1_000L, 1, "가".repeat(100), "나".repeat(500));

        assertThat(order.getProductName()).hasSize(100);
        assertThat(order.getRequestNote()).hasSize(500);
    }

    @Test
    @DisplayName("상품명이 100자를 넘으면 주문을 거부한다")
    void rejectsLongProductName() {
        assertThatThrownBy(() -> create(1_000L, 1, "가".repeat(101), null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("요청사항이 500자를 넘으면 주문을 거부한다")
    void rejectsLongRequestNote() {
        assertThatThrownBy(() -> create(1_000L, 1, "상품", "가".repeat(501)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_REQUEST);
    }

    private static Order create(long unitPrice, int quantity, String productName, String requestNote) {
        return Order.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), productName, unitPrice, quantity, UUID.randomUUID(), requestNote);
    }
}
