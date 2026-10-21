package com.sparta.logistics.order.application;

import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.order.application.dto.CancelOrderResult;
import com.sparta.logistics.order.application.dto.CancellationAttempt;
import com.sparta.logistics.order.application.dto.CancellationCompletionOutcome;
import com.sparta.logistics.order.domain.CancellationFailureReason;
import com.sparta.logistics.order.domain.OrderStatus;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCancellationService {

    private final OrderCancellationWriter writer;
    private final DeliveryGateway deliveryGateway;

    /** O-05. 실행권 확보 → 배송 취소 → 주문 커밋. 확인된 로컬 롤백만 보상한다. */
    @Transactional(propagation = Propagation.NEVER)
    public CancelOrderResult cancel(UserContext user, UUID orderId) {
        UUID attemptId = UUID.randomUUID();
        CancellationAttempt attempt;
        try {
            attempt = writer.start(user, orderId, attemptId);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("주문 취소 시작 실패: orderId={}, attemptId={}, cause={}", orderId, attemptId, exception.getClass().getSimpleName());
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        cancelDelivery(attempt);
        try {
            return writer.complete(attempt);
        } catch (RuntimeException exception) {
            return handleCompletionFailure(attempt, exception);
        }
    }

    private void cancelDelivery(CancellationAttempt attempt) {
        try {
            deliveryGateway.cancel(attempt.deliveryId());
        } catch (BusinessException exception) {
            if (exception.getErrorCode().status().is4xxClientError()) {
                abortSafely(attempt);
                throw exception;
            }
            markRecoverySafely(attempt, CancellationFailureReason.DELIVERY_RESULT_UNKNOWN, exception);
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        } catch (RuntimeException exception) {
            // Note: 타임아웃 뒤 원격 취소가 늦게 완료될 수 있다. I-12를 먼저 보내도 순서가 뒤집힐 수 있어 자동 원복하지 않는다.
            markRecoverySafely(attempt, CancellationFailureReason.DELIVERY_RESULT_UNKNOWN, exception);
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    private CancelOrderResult handleCompletionFailure(CancellationAttempt attempt, RuntimeException failure) {
        CancellationCompletionOutcome outcome;
        try {
            outcome = writer.resolveCompletion(attempt);
        } catch (RuntimeException verificationFailure) {
            markRecoverySafely(attempt, CancellationFailureReason.ORDER_COMMIT_UNKNOWN, verificationFailure);
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
        if (outcome == CancellationCompletionOutcome.COMMITTED) {
            log.warn("주문 취소 커밋 재확인: orderId={}, attemptId={}", attempt.orderId(), attempt.attemptId());
            return new CancelOrderResult(attempt.orderId(), OrderStatus.CANCELED);
        }
        if (outcome != CancellationCompletionOutcome.ROLLED_BACK) {
            markRecoverySafely(attempt, CancellationFailureReason.ORDER_COMMIT_UNKNOWN, failure);
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        log.error("주문 취소 저장 롤백 확인: orderId={}, attemptId={}, cause={}",
                attempt.orderId(), attempt.attemptId(), failure.getClass().getSimpleName());
        try {
            deliveryGateway.revertCancellation(attempt.deliveryId());
        } catch (RuntimeException compensationFailure) {
            markRecoverySafely(attempt, CancellationFailureReason.REVERT_FAILED, compensationFailure);
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
        abortSafely(attempt);
        throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    private void abortSafely(CancellationAttempt attempt) {
        try {
            writer.abort(attempt);
        } catch (RuntimeException exception) {
            // Note: 진행 기록 해제가 실패하면 PROCESSING이 남아 재실행을 막는다. 최초 오류 응답을 덮지 않는다.
            log.error("주문 취소 종료 기록 실패: orderId={}, attemptId={}, cause={}",
                    attempt.orderId(), attempt.attemptId(), exception.getClass().getSimpleName());
        }
    }

    private void markRecoverySafely(CancellationAttempt attempt, CancellationFailureReason reason, RuntimeException failure) {
        log.error("주문 취소 복구 필요: orderId={}, attemptId={}, reason={}, cause={}",
                attempt.orderId(), attempt.attemptId(), reason, failure.getClass().getSimpleName());
        try {
            writer.requireRecovery(attempt, reason);
        } catch (RuntimeException recordFailure) {
            log.error("주문 취소 복구 기록 실패: orderId={}, attemptId={}, reason={}, cause={}",
                    attempt.orderId(), attempt.attemptId(), reason, recordFailure.getClass().getSimpleName());
        }
    }
}
