package com.sparta.logistics.order.application;

import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.order.application.dto.OrderDeletionOutcome;
import com.sparta.logistics.order.application.dto.OrderDeletionTarget;
import com.sparta.logistics.order.domain.DeletionFailureReason;
import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.domain.OrderCancellationRepository;
import com.sparta.logistics.order.domain.OrderCancellationStatus;
import com.sparta.logistics.order.domain.OrderDeletion;
import com.sparta.logistics.order.domain.OrderDeletionRepository;
import com.sparta.logistics.order.domain.OrderDeletionStatus;
import com.sparta.logistics.order.domain.OrderErrorCode;
import com.sparta.logistics.order.domain.OrderRepository;
import com.sparta.logistics.order.domain.OrderStatus;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderDeletionWriter {
    private final OrderRepository orders;
    private final OrderDeletionRepository deletions;
    private final OrderCancellationRepository cancellations;
    private final DeliveryGateway delivery;

    /** O-06. 재시도는 저장된 허가 근거를 사용하므로 이미 삭제된 배송을 다시 GET하지 않는다. */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public OrderDeletionTarget inspect(UUID orderId) {
        Order order = orders.findById(orderId).orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        OrderDeletion existing = deletions.findByOrderIdIncludingDeleted(orderId).orElse(null);
        if (existing != null) existing.validateResumable(order);
        validateNoCancellation(orderId);
        return new OrderDeletionTarget(orderId, order.getDeliveryId(), existing == null && order.getStatus() != OrderStatus.CANCELED);
    }

    /** O-06. 주문 잠금 아래 삭제 조건·취소 상태를 다시 확인하고 허가 근거를 별도 커밋한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void start(OrderDeletionTarget target, boolean deliveryCompleted) {
        Order order = findOrder(target.orderId());
        if (!order.getDeliveryId().equals(target.deliveryId())) throw new BusinessException(OrderErrorCode.ORDER_NOT_DELETABLE);
        validateNoCancellation(order.getId());
        OrderDeletion existing = deletions.findByOrderIdIncludingDeleted(order.getId()).orElse(null);
        if (existing != null) {
            existing.validateResumable(order);
        } else {
            deletions.saveAndFlush(OrderDeletion.start(UUID.randomUUID(), order, deliveryCompleted));
        }
    }

    /** O-06. SA 7.5의 로컬 삭제·I-13 경계. 원격 실패 시 로컬 삭제와 완료 기록은 함께 롤백한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void delete(UserContext user, UUID orderId) {
        Order order = findOrder(orderId);
        OrderDeletion record = findDeletion(orderId);
        record.validateResumable(order);
        validateNoCancellation(orderId);
        order.softDelete(user.username());
        // Note: 로컬 제약 오류는 원격 삭제 전에 확인한다. 잠금은 I-13과 커밋이 끝날 때까지 유지한다(SA 7.5).
        orders.flush();
        delivery.deleteByOrderId(orderId);
        record.complete();
        deletions.flush();
    }

    /** O-06. 같은 주문 잠금으로 이전 커밋이 끝날 때까지 기다리고 삭제된 행·완료 기록을 함께 확인한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderDeletionOutcome resolve(UUID orderId) {
        Order order = orders.findByIdIncludingDeletedForUpdate(orderId).orElse(null);
        OrderDeletion record = deletions.findByOrderIdIncludingDeleted(orderId).orElse(null);
        if (order == null || record == null || record.isDeleted() || !record.getDeliveryId().equals(order.getDeliveryId())) {
            return OrderDeletionOutcome.UNKNOWN;
        }
        if (order.isDeleted() && record.getStatus() == OrderDeletionStatus.COMPLETED) return OrderDeletionOutcome.COMMITTED;
        if (!order.isDeleted() && record.getStatus() != OrderDeletionStatus.COMPLETED) return OrderDeletionOutcome.NOT_COMMITTED;
        return OrderDeletionOutcome.UNKNOWN;
    }

    /** O-06. 재확인 실패도 기존 삭제 허가 근거를 없애지 않는다. 완료 기록은 덮지 않는다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requireRecovery(UUID orderId, DeletionFailureReason reason) {
        Order order = orders.findByIdIncludingDeletedForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        OrderDeletion record = findDeletion(orderId);
        if (order.isDeleted() && record.getStatus() == OrderDeletionStatus.COMPLETED) return;
        record.validateResumable(order);
        record.requireRecovery(reason);
    }

    private Order findOrder(UUID orderId) {
        return orders.findByIdForUpdate(orderId).orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    private OrderDeletion findDeletion(UUID orderId) {
        return deletions.findByOrderIdIncludingDeleted(orderId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR));
    }

    private void validateNoCancellation(UUID orderId) {
        if (cancellations.existsByOrderIdAndStatusIn(orderId,
                Set.of(OrderCancellationStatus.PROCESSING, OrderCancellationStatus.RECOVERY_REQUIRED))) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_DELETABLE, "취소가 진행 중이거나 결과 확인이 필요한 주문은 삭제할 수 없습니다.");
        }
    }
}
