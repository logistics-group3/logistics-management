package com.sparta.logistics.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.logistics.common.exception.CommonErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderDeletionTest {
    @Test
    @DisplayName("취소된 주문의 삭제 허가 근거와 배송 ID를 저장한다")
    void recordsCanceledOrder() {
        Order order = order();
        order.cancel();
        OrderDeletion record = OrderDeletion.start(UUID.randomUUID(), order, false);
        assertThat(record.getEligibility()).isEqualTo(DeletionEligibility.ORDER_CANCELED);
        assertThat(record.getStatus()).isEqualTo(OrderDeletionStatus.PROCESSING);
        assertThat(record.getDeliveryId()).isEqualTo(order.getDeliveryId());
        assertThat(record.getOrderId()).isEqualTo(order.getId());
    }

    @Test
    @DisplayName("배송 완료가 확인된 ORDERED 주문도 삭제할 수 있다")
    void recordsDeliveredOrder() {
        OrderDeletion record = OrderDeletion.start(UUID.randomUUID(), order(), true);
        assertThat(record.getEligibility()).isEqualTo(DeletionEligibility.DELIVERY_DELIVERED);
    }

    @Test
    @DisplayName("취소·배송 완료 어느 조건도 충족하지 않으면 삭제 기록을 만들지 않는다")
    void rejectsActiveOrder() {
        assertThatThrownBy(() -> OrderDeletion.start(UUID.randomUUID(), order(), false))
                .extracting("errorCode").isEqualTo(OrderErrorCode.ORDER_NOT_DELETABLE);
    }

    @Test
    @DisplayName("삭제 결과 확인이 필요해도 원래 허가 근거를 보존하고 같은 삭제를 완료할 수 있다")
    void resumesRecovery() {
        Order order = order();
        OrderDeletion record = OrderDeletion.start(UUID.randomUUID(), order, true);
        record.requireRecovery(DeletionFailureReason.ORDER_WRITE_FAILED);
        assertThat(record.getStatus()).isEqualTo(OrderDeletionStatus.RECOVERY_REQUIRED);
        record.validateResumable(order);
        record.complete();
        assertThat(record.getStatus()).isEqualTo(OrderDeletionStatus.COMPLETED);
        assertThat(record.getFailureReason()).isNull();
        assertThat(record.getEligibility()).isEqualTo(DeletionEligibility.DELIVERY_DELIVERED);
    }

    @Test
    @DisplayName("완료된 삭제 기록을 복구 대기로 되돌리거나 다시 완료하지 않는다")
    void preservesCompletion() {
        Order order = order();
        OrderDeletion record = OrderDeletion.start(UUID.randomUUID(), order, true);
        record.complete();
        assertThatThrownBy(() -> record.requireRecovery(DeletionFailureReason.ORDER_COMMIT_UNKNOWN))
                .extracting("errorCode").isEqualTo(OrderErrorCode.ORDER_NOT_DELETABLE);
        assertThatThrownBy(record::complete).extracting("errorCode").isEqualTo(OrderErrorCode.ORDER_NOT_DELETABLE);
    }

    @Test
    @DisplayName("삭제 이력이 논리 삭제되거나 주문·배송이 다르면 재실행을 허용하지 않는다")
    void rejectsInvalidRecord() {
        Order order = order();
        OrderDeletion record = OrderDeletion.start(UUID.randomUUID(), order, true);
        assertThatThrownBy(() -> record.validateResumable(order()))
                .extracting("errorCode").isEqualTo(OrderErrorCode.ORDER_NOT_DELETABLE);
        record.softDelete("fixture");
        assertThatThrownBy(() -> record.validateResumable(order))
                .extracting("errorCode").isEqualTo(OrderErrorCode.ORDER_NOT_DELETABLE);
    }

    @Test
    @DisplayName("삭제 기록과 복구 사유의 필수 입력을 검증한다")
    void validatesRequiredValues() {
        assertThatThrownBy(() -> OrderDeletion.start(null, order(), true))
                .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> OrderDeletion.start(UUID.randomUUID(), null, true))
                .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> OrderDeletion.start(UUID.randomUUID(), order(), true).requireRecovery(null))
                .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_REQUEST);
    }

    private Order order() {
        return Order.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "상품", 12000L, 2, UUID.randomUUID(), "요청사항");
    }
}
