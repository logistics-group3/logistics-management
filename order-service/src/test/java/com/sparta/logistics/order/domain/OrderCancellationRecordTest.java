package com.sparta.logistics.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.logistics.common.exception.CommonErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OrderCancellationRecordTest {

    @Test
    @DisplayName("진행 기록은 지정한 시도·주문·배송 ID와 PROCESSING 상태로 시작한다")
    void startsAttempt() {
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();

        OrderCancellation cancellation = OrderCancellation.start(id, orderId, deliveryId);

        assertThat(cancellation.getId()).isEqualTo(id);
        assertThat(cancellation.getOrderId()).isEqualTo(orderId);
        assertThat(cancellation.getDeliveryId()).isEqualTo(deliveryId);
        assertThat(cancellation.getStatus()).isEqualTo(OrderCancellationStatus.PROCESSING);
        assertThat(cancellation.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("필수 식별자가 없으면 취소 기록을 만들지 않는다")
    void rejectsMissingIdentity() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> OrderCancellation.start(null, id, id)).extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> OrderCancellation.start(id, null, id)).extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> OrderCancellation.start(id, id, null)).extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_REQUEST);
    }

    @ParameterizedTest
    @EnumSource(value = OrderCancellationStatus.class, names = {"COMPLETED", "ABORTED", "RECOVERY_REQUIRED"})
    @DisplayName("종료되거나 복구 대기인 시도를 완료·중단·복구 상태로 다시 바꾸지 않는다")
    void preventsReusingAttempt(OrderCancellationStatus target) {
        OrderCancellation cancellation = attempt();
        switch (target) {
            case COMPLETED -> cancellation.complete();
            case ABORTED -> cancellation.abort();
            case RECOVERY_REQUIRED -> cancellation.requireRecovery(CancellationFailureReason.REVERT_FAILED);
            default -> throw new AssertionError(target);
        }

        assertThat(cancellation.getStatus()).isEqualTo(target);
        assertThatThrownBy(cancellation::complete).extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_STATUS_TRANSITION);
        assertThatThrownBy(cancellation::abort).extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_STATUS_TRANSITION);
        assertThatThrownBy(() -> cancellation.requireRecovery(CancellationFailureReason.REVERT_FAILED))
                .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_STATUS_TRANSITION);
    }

    @ParameterizedTest
    @EnumSource(CancellationFailureReason.class)
    @DisplayName("복구 대기에는 결과 불확실·원복 실패 사유를 기록한다")
    void recordsRecoveryReason(CancellationFailureReason reason) {
        OrderCancellation cancellation = attempt();

        cancellation.requireRecovery(reason);

        assertThat(cancellation.getStatus()).isEqualTo(OrderCancellationStatus.RECOVERY_REQUIRED);
        assertThat(cancellation.getFailureReason()).isEqualTo(reason);
    }

    @Test
    @DisplayName("사유 없이 복구 대기로 전환하지 않는다")
    void requiresRecoveryReason() {
        OrderCancellation cancellation = attempt();

        assertThatThrownBy(() -> cancellation.requireRecovery(null)).extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_REQUEST);
        assertThat(cancellation.getStatus()).isEqualTo(OrderCancellationStatus.PROCESSING);
    }

    private OrderCancellation attempt() {
        return OrderCancellation.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }
}
