package com.sparta.logistics.order.application;

import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.order.application.dto.CancelOrderResult;
import com.sparta.logistics.order.application.dto.CancellationAttempt;
import com.sparta.logistics.order.application.dto.CancellationCompletionOutcome;
import com.sparta.logistics.order.domain.CancellationFailureReason;
import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.domain.OrderCancellation;
import com.sparta.logistics.order.domain.OrderCancellationRepository;
import com.sparta.logistics.order.domain.OrderCancellationStatus;
import com.sparta.logistics.order.domain.OrderErrorCode;
import com.sparta.logistics.order.domain.OrderRepository;
import com.sparta.logistics.order.domain.OrderStatus;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 취소 Saga의 짧은 로컬 트랜잭션. 원격 호출을 포함하지 않는다. */
@Service
@RequiredArgsConstructor
public class OrderCancellationWriter {

    private final OrderRepository orderRepository;
    private final OrderCancellationRepository cancellationRepository;

    /** O-05. 권한·상태 확인과 실행권 획득을 커밋한 뒤 배송을 호출할 수 있게 한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CancellationAttempt start(UserContext user, UUID orderId, UUID attemptId) {
        Order order = findOrder(orderId);
        user.validateSelfOrMaster(order.getOrdererId());
        order.validateCancelable();
        if (cancellationRepository.existsByOrderIdAndStatusIn(orderId,
                Set.of(OrderCancellationStatus.PROCESSING, OrderCancellationStatus.RECOVERY_REQUIRED))) {
            throw new BusinessException(CommonErrorCode.INVALID_STATUS_TRANSITION, "주문 취소가 진행 중이거나 결과 확인이 필요합니다.");
        }
        return CancellationAttempt.from(cancellationRepository.saveAndFlush(
                OrderCancellation.start(attemptId, orderId, order.getDeliveryId())));
    }

    /** O-05. 주문 취소와 시도 완료를 같은 커밋으로 확정한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CancelOrderResult complete(CancellationAttempt attempt) {
        Order order = findOrder(attempt.orderId());
        OrderCancellation cancellation = findCancellation(attempt);
        cancellation.validateProcessing();
        order.cancel();
        cancellation.complete();
        cancellationRepository.flush();
        return CancelOrderResult.from(order);
    }

    /** O-05. 명확한 거절이나 확인된 원복 뒤에만 실행권을 해제한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void abort(CancellationAttempt attempt) {
        findCancellation(attempt).abort();
    }

    /** O-05. 결과가 불확실하면 실행권을 남겨 새로운 취소·보상이 겹치지 않게 한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requireRecovery(CancellationAttempt attempt, CancellationFailureReason reason) {
        findCancellation(attempt).requireRecovery(reason);
    }

    /** O-05. 커밋 예외가 실제 롤백인지 새 트랜잭션에서 확인한 뒤에만 원격 보상을 허용한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CancellationCompletionOutcome resolveCompletion(CancellationAttempt attempt) {
        // Note: complete와 같은 순서로 잠근다. 이전 트랜잭션의 커밋·롤백이 끝난 상태를 읽어 성공한 취소의 원복을 막는다.
        Order order = orderRepository.findByIdForUpdate(attempt.orderId()).orElse(null);
        OrderCancellation cancellation = findCancellation(attempt);
        if (order != null && order.getStatus() == OrderStatus.CANCELED
                && cancellation.getStatus() == OrderCancellationStatus.COMPLETED) {
            return CancellationCompletionOutcome.COMMITTED;
        }
        if (order != null && order.getStatus() == OrderStatus.ORDERED
                && cancellation.getStatus() == OrderCancellationStatus.PROCESSING) {
            return CancellationCompletionOutcome.ROLLED_BACK;
        }
        return CancellationCompletionOutcome.UNKNOWN;
    }

    private Order findOrder(UUID orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    private OrderCancellation findCancellation(CancellationAttempt attempt) {
        OrderCancellation cancellation = cancellationRepository.findByIdForUpdate(attempt.attemptId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR));
        if (!cancellation.getOrderId().equals(attempt.orderId()) || !cancellation.getDeliveryId().equals(attempt.deliveryId())) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
        return cancellation;
    }
}
