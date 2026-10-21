package com.sparta.logistics.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.order.application.dto.CancelOrderResult;
import com.sparta.logistics.order.application.dto.CancellationAttempt;
import com.sparta.logistics.order.application.dto.CancellationCompletionOutcome;
import com.sparta.logistics.order.domain.CancellationFailureReason;
import com.sparta.logistics.order.domain.OrderErrorCode;
import com.sparta.logistics.order.domain.OrderStatus;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderCancellationServiceTest {

    @Mock
    private OrderCancellationWriter writer;
    @Mock
    private DeliveryGateway delivery;

    private final UserContext user = new UserContext(UUID.randomUUID(), "junhyun", Role.USER, false);
    private final CancellationAttempt attempt = new CancellationAttempt(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    private OrderCancellationService service;

    @BeforeEach
    void setUp() {
        service = new OrderCancellationService(writer, delivery);
    }

    @Test
    @DisplayName("실행권 확보·배송 취소·주문 커밋 순서로 취소하고 원복은 호출하지 않는다")
    void completesCancellation() {
        start();
        CancelOrderResult result = new CancelOrderResult(attempt.orderId(), OrderStatus.CANCELED);
        given(writer.complete(attempt)).willReturn(result);

        assertThat(service.cancel(user, attempt.orderId())).isEqualTo(result);

        InOrder sequence = inOrder(writer, delivery);
        sequence.verify(writer).start(eq(user), eq(attempt.orderId()), any());
        sequence.verify(delivery).cancel(attempt.deliveryId());
        sequence.verify(writer).complete(attempt);
        verify(delivery, never()).revertCancellation(any());
    }

    @Test
    @DisplayName("권한·상태·주문 없음으로 시작이 거부되면 배송을 호출하지 않는다")
    void startFailureDoesNotCallDelivery() {
        given(writer.start(eq(user), eq(attempt.orderId()), any())).willThrow(new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        assertThatThrownBy(() -> service.cancel(user, attempt.orderId())).extracting("errorCode").isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
        verifyNoInteractions(delivery);
    }

    @Test
    @DisplayName("시작 트랜잭션 오류는 500이며 원격 취소를 시작하지 않는다")
    void startTransactionFailure() {
        given(writer.start(eq(user), eq(attempt.orderId()), any())).willThrow(new IllegalStateException("DB unavailable"));

        assertFailure(CommonErrorCode.INTERNAL_SERVER_ERROR);
        verifyNoInteractions(delivery);
    }

    @Test
    @DisplayName("배송의 명확한 409는 진행 기록을 종료하고 원래 오류를 전달한다")
    void propagatesDeliveryRejection() {
        start();
        willThrow(new BusinessException(CommonErrorCode.INVALID_STATUS_TRANSITION)).given(delivery).cancel(attempt.deliveryId());

        assertFailure(CommonErrorCode.INVALID_STATUS_TRANSITION);

        verify(writer).abort(attempt);
        verify(writer, never()).complete(any());
        verify(delivery, never()).revertCancellation(any());
    }

    @ParameterizedTest
    @MethodSource("uncertainFailures")
    @DisplayName("배송 결과를 모르는 실패는 503·복구 대기이며 자동 재호출이나 원복을 하지 않는다")
    void protectsUncertainDeliveryResult(RuntimeException failure) {
        start();
        willThrow(failure).given(delivery).cancel(attempt.deliveryId());

        assertFailure(CommonErrorCode.SERVICE_UNAVAILABLE);

        verify(writer).requireRecovery(attempt, CancellationFailureReason.DELIVERY_RESULT_UNKNOWN);
        verify(writer, never()).complete(any());
        verify(writer, never()).abort(any());
        verify(delivery, never()).revertCancellation(any());
    }

    @Test
    @DisplayName("주문 저장의 롤백이 확인되면 배송을 원복한 후 진행 기록을 종료한다")
    void compensatesConfirmedRollback() {
        failingCompletion(CancellationCompletionOutcome.ROLLED_BACK);

        assertFailure(CommonErrorCode.INTERNAL_SERVER_ERROR);

        InOrder sequence = inOrder(writer, delivery);
        sequence.verify(writer).complete(attempt);
        sequence.verify(writer).resolveCompletion(attempt);
        sequence.verify(delivery).revertCancellation(attempt.deliveryId());
        sequence.verify(writer).abort(attempt);
    }

    @Test
    @DisplayName("커밋 오류 뒤 실제 완료가 확인되면 200을 반환하고 정상 취소를 원복하지 않는다")
    void returnsConfirmedCommittedResult() {
        failingCompletion(CancellationCompletionOutcome.COMMITTED);

        assertThat(service.cancel(user, attempt.orderId())).isEqualTo(new CancelOrderResult(attempt.orderId(), OrderStatus.CANCELED));
        verify(delivery, never()).revertCancellation(any());
        verify(writer, never()).abort(any());
    }

    @Test
    @DisplayName("커밋 결과가 불명확하면 주문 취소 결과 확인 대상으로 남기고 자동 원복하지 않는다")
    void doesNotRevertUnknownCommit() {
        failingCompletion(CancellationCompletionOutcome.UNKNOWN);

        assertFailure(CommonErrorCode.INTERNAL_SERVER_ERROR);

        verify(writer).requireRecovery(attempt, CancellationFailureReason.ORDER_COMMIT_UNKNOWN);
        verify(delivery, never()).revertCancellation(any());
    }

    @Test
    @DisplayName("커밋 재확인마저 실패하면 최초 취소를 자동 원복하지 않는다")
    void protectsWhenDatabaseCannotBeChecked() {
        start();
        given(writer.complete(attempt)).willThrow(new IllegalStateException("commit failed"));
        given(writer.resolveCompletion(attempt)).willThrow(new IllegalStateException("read failed"));

        assertFailure(CommonErrorCode.INTERNAL_SERVER_ERROR);

        verify(writer).requireRecovery(attempt, CancellationFailureReason.ORDER_COMMIT_UNKNOWN);
        verify(delivery, never()).revertCancellation(any());
    }

    @Test
    @DisplayName("배송 원복이 실패해도 원래 저장 실패 500을 유지하고 복구 대상으로 남긴다")
    void preservesOriginalFailureWhenCompensationFails() {
        failingCompletion(CancellationCompletionOutcome.ROLLED_BACK);
        willThrow(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE)).given(delivery).revertCancellation(attempt.deliveryId());

        assertFailure(CommonErrorCode.INTERNAL_SERVER_ERROR);

        verify(writer).requireRecovery(attempt, CancellationFailureReason.REVERT_FAILED);
        verify(writer, never()).abort(any());
    }

    @Test
    @DisplayName("복구 기록 DB 오류가 배송 실패 503을 덮지 않는다")
    void preservesDeliveryFailureWhenRecordingFails() {
        start();
        willThrow(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE)).given(delivery).cancel(attempt.deliveryId());
        willThrow(new IllegalStateException("record failed")).given(writer).requireRecovery(attempt, CancellationFailureReason.DELIVERY_RESULT_UNKNOWN);

        assertFailure(CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("진행 기록 종료 오류가 배송의 명확한 409를 덮지 않는다")
    void preservesRejectionWhenAbortFails() {
        start();
        willThrow(new BusinessException(CommonErrorCode.INVALID_STATUS_TRANSITION)).given(delivery).cancel(attempt.deliveryId());
        willThrow(new IllegalStateException("abort failed")).given(writer).abort(attempt);

        assertFailure(CommonErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("원복 후 진행 기록 종료가 실패해도 저장 실패 500을 유지한다")
    void preservesLocalFailureWhenAbortFails() {
        failingCompletion(CancellationCompletionOutcome.ROLLED_BACK);
        willThrow(new IllegalStateException("abort failed")).given(writer).abort(attempt);

        assertFailure(CommonErrorCode.INTERNAL_SERVER_ERROR);
        verify(delivery).revertCancellation(attempt.deliveryId());
    }

    private void start() {
        given(writer.start(eq(user), eq(attempt.orderId()), any())).willReturn(attempt);
    }

    private void failingCompletion(CancellationCompletionOutcome outcome) {
        start();
        given(writer.complete(attempt)).willThrow(new IllegalStateException("commit failed"));
        given(writer.resolveCompletion(attempt)).willReturn(outcome);
    }

    private void assertFailure(CommonErrorCode code) {
        assertThatThrownBy(() -> service.cancel(user, attempt.orderId())).extracting("errorCode").isEqualTo(code);
    }

    private static Stream<RuntimeException> uncertainFailures() {
        return Stream.of(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE), new IllegalStateException("decode or transport failure"));
    }
}
