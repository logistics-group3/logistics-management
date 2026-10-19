package com.sparta.logistics.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.logistics.common.exception.CommonErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OrderCreationTest {

    @Test
    @DisplayName("생성 진행 기록은 주문·주문자 ID를 보관하고 배송 ID 없이 시작한다")
    void startsBeforeDelivery() {
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID ordererId = UUID.randomUUID();

        OrderCreation creation = OrderCreation.start(id, orderId, ordererId);

        assertThat(creation.getId()).isEqualTo(id);
        assertThat(creation.getOrderId()).isEqualTo(orderId);
        assertThat(creation.getOrdererId()).isEqualTo(ordererId);
        assertThat(creation.getStatus()).isEqualTo(OrderCreationStatus.PROCESSING);
        assertThat(creation.getDeliveryId()).isNull();
    }

    @Test
    @DisplayName("생성이 완료되면 확인한 배송 ID를 함께 보관한다")
    void completesWithDelivery() {
        OrderCreation creation = attempt();
        UUID deliveryId = UUID.randomUUID();

        creation.complete(deliveryId);

        assertThat(creation.getStatus()).isEqualTo(OrderCreationStatus.COMPLETED);
        assertThat(creation.getDeliveryId()).isEqualTo(deliveryId);
    }

    @Test
    @DisplayName("배송 ID 없이 완료로 기록할 수 없다")
    void rejectsCompletionWithoutDelivery() {
        OrderCreation creation = attempt();

        assertThatThrownBy(() -> creation.complete(null)).extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_REQUEST);
        assertThat(creation.getStatus()).isEqualTo(OrderCreationStatus.PROCESSING);
    }

    @ParameterizedTest
    @EnumSource(CreationFailureReason.class)
    @DisplayName("생성 실패의 확인된 배송 ID와 복구 사유를 보관한다")
    void recordsRecoveryReason(CreationFailureReason reason) {
        OrderCreation creation = attempt();
        UUID deliveryId = UUID.randomUUID();

        creation.requireRecovery(deliveryId, reason);

        assertThat(creation.getStatus()).isEqualTo(OrderCreationStatus.RECOVERY_REQUIRED);
        assertThat(creation.getDeliveryId()).isEqualTo(deliveryId);
        assertThat(creation.getFailureReason()).isEqualTo(reason);
    }

    @Test
    @DisplayName("배송 생성 결과를 모르면 배송 ID 없이도 복구 대상으로 남길 수 있다")
    void recordsUnknownDelivery() {
        OrderCreation creation = attempt();

        creation.requireRecovery(null, CreationFailureReason.DELIVERY_RESULT_UNKNOWN);

        assertThat(creation.getDeliveryId()).isNull();
        assertThat(creation.getStatus()).isEqualTo(OrderCreationStatus.RECOVERY_REQUIRED);
    }

    @ParameterizedTest
    @EnumSource(value = OrderCreationStatus.class, names = {"COMPLETED", "ABORTED", "RECOVERY_REQUIRED"})
    @DisplayName("종료·복구 대기 기록을 다른 결과로 덮어쓰지 않는다")
    void preservesTerminalState(OrderCreationStatus status) {
        OrderCreation creation = attempt();
        UUID deliveryId = UUID.randomUUID();
        switch (status) {
            case COMPLETED -> creation.complete(deliveryId);
            case ABORTED -> creation.abort(deliveryId, null, null);
            case RECOVERY_REQUIRED -> creation.requireRecovery(deliveryId, CreationFailureReason.ORDER_COMMIT_UNKNOWN);
            default -> throw new AssertionError(status);
        }

        assertThat(creation.getStatus()).isEqualTo(status);
        assertThatThrownBy(() -> creation.complete(deliveryId)).extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_STATUS_TRANSITION);
        assertThatThrownBy(() -> creation.abort(deliveryId, null, null)).extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_STATUS_TRANSITION);
        assertThatThrownBy(() -> creation.requireRecovery(deliveryId, CreationFailureReason.DELIVERY_DELETE_FAILED))
                .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("생성 진행 기록의 필수 ID와 복구 사유를 검증한다")
    void validatesIdentityAndReason() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> OrderCreation.start(null, id, id)).extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> OrderCreation.start(id, null, id)).extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> OrderCreation.start(id, id, null)).extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> attempt().requireRecovery(id, null)).extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_REQUEST);
    }

    private OrderCreation attempt() {
        return OrderCreation.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }
}
