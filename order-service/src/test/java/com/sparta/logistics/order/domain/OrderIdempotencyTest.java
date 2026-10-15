package com.sparta.logistics.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.logistics.common.exception.CommonErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OrderIdempotencyTest {

    private final UUID orderId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final OrderRequestIdentity identity = new OrderRequestIdentity("same-key", "v1:" + "a".repeat(64));

    @Test
    @DisplayName("최초 생성 스냅샷은 원본 Order가 수정·취소된 뒤에도 같은 결과를 반환한다")
    void preservesOriginalResponse() {
        Order order = order();
        OrderCreation creation = creation();
        creation.completeWithSnapshot(order);
        OrderCreationSnapshot original = creation.replay(identity);

        order.updateRequestNote("바뀐 요청사항");
        order.cancel();

        assertThat(creation.replay(identity)).isEqualTo(original);
        assertThat(original.requestNote()).isEqualTo("원래 요청사항");
        assertThat(original.status()).isEqualTo(OrderStatus.ORDERED);
    }

    @ParameterizedTest
    @EnumSource(value = OrderCreationStatus.class, names = {"PROCESSING", "ABORTED", "RECOVERY_REQUIRED"})
    @DisplayName("미완료·실패한 같은 키는 재실행하지 않고 구체적인 충돌 코드를 반환한다")
    void rejectsNonCompletedAttempts(OrderCreationStatus status) {
        OrderCreation creation = creation();
        OrderErrorCode expected = switch (status) {
            case PROCESSING -> OrderErrorCode.ORDER_CREATION_IN_PROGRESS;
            case ABORTED -> { creation.abort(null); yield OrderErrorCode.ORDER_CREATION_ABORTED; }
            case RECOVERY_REQUIRED -> {
                creation.requireRecovery(null, CreationFailureReason.DELIVERY_RESULT_UNKNOWN);
                yield OrderErrorCode.ORDER_CREATION_RECOVERY_REQUIRED;
            }
            default -> throw new AssertionError(status);
        };

        assertThatThrownBy(() -> creation.replay(identity)).extracting("errorCode").isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(OrderCreationStatus.class)
    @DisplayName("같은 키에 다른 지문은 진행 상태와 무관하게 충돌이다")
    void rejectsChangedRequest(OrderCreationStatus status) {
        OrderCreation creation = creation();
        switch (status) {
            case COMPLETED -> creation.completeWithSnapshot(order());
            case ABORTED -> creation.abort(null);
            case RECOVERY_REQUIRED -> creation.requireRecovery(null, CreationFailureReason.DELIVERY_RESULT_UNKNOWN);
            default -> { }
        }

        assertThatThrownBy(() -> creation.replay(new OrderRequestIdentity("same-key", "v1:" + "b".repeat(64))))
                .extracting("errorCode").isEqualTo(OrderErrorCode.ORDER_IDEMPOTENCY_KEY_CONFLICT);
    }

    @Test
    @DisplayName("키 있는 기록은 응답 스냅샷 없이 완료할 수 없다")
    void requiresSnapshot() {
        OrderCreation creation = creation();

        assertThatThrownBy(() -> creation.complete(UUID.randomUUID())).extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_REQUEST);
        assertThat(creation.getStatus()).isEqualTo(OrderCreationStatus.PROCESSING);
    }

    @Test
    @DisplayName("다른 주문의 응답을 완료 스냅샷으로 저장하지 않는다")
    void rejectsWrongOrderSnapshot() {
        Order different = Order.create(UUID.randomUUID(), ownerId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "상품", 100L, 2, UUID.randomUUID(), null);

        assertThatThrownBy(() -> creation().completeWithSnapshot(different)).extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("논리 삭제된 기록의 키는 재사용하거나 응답을 재생하지 않는다")
    void keepsDeletedKeyReserved() {
        OrderCreation creation = creation();
        creation.completeWithSnapshot(order());
        creation.softDelete("operator");

        assertThatThrownBy(() -> creation.replay(identity)).extracting("errorCode").isEqualTo(OrderErrorCode.ORDER_CREATION_RECOVERY_REQUIRED);
    }

    private OrderCreation creation() {
        return OrderCreation.start(UUID.randomUUID(), orderId, ownerId, identity);
    }

    private Order order() {
        return Order.create(orderId, ownerId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "상품", 100L, 2, UUID.randomUUID(), "원래 요청사항");
    }
}
