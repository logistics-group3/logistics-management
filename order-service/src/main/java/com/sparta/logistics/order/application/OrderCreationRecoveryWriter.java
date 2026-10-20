package com.sparta.logistics.order.application;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.order.application.dto.OrderCreationRecoveryResult;
import com.sparta.logistics.order.application.dto.RecoverOrderCreationCommand;
import com.sparta.logistics.order.domain.OrderCreation;
import com.sparta.logistics.order.domain.OrderCreationRecovery;
import com.sparta.logistics.order.domain.OrderCreationRecoveryEvent;
import com.sparta.logistics.order.domain.OrderCreationRecoveryEventRepository;
import com.sparta.logistics.order.domain.OrderCreationRecoveryRepository;
import com.sparta.logistics.order.domain.OrderCreationRepository;
import com.sparta.logistics.order.domain.OrderCreationStatus;
import com.sparta.logistics.order.domain.OrderErrorCode;
import com.sparta.logistics.order.domain.OrderRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class OrderCreationRecoveryWriter {
    private final OrderCreationRepository creations;
    private final OrderRepository orders;
    private final OrderCreationRecoveryRepository recoveries;
    private final OrderCreationRecoveryEventRepository events;

    public record Claim(UUID recoveryId, UUID orderId, UUID executionId, boolean execute) { }

    /** O-09 정상 저장과 같은 생성 행을 잠가, 저장 또는 복구 중 하나만 먼저 확정한다. */
    public Claim claim(UUID orderId, UUID actor, RecoverOrderCreationCommand command) {
        OrderCreation creation = creation(orderId);
        if (!creation.getId().equals(command.attemptId())) throw conflict();
        OrderCreationRecovery recovery = recoveries.findByIdForUpdate(command.recoveryId()).orElse(null);
        if (recovery != null) {
            recovery.validateRequest(command.attemptId(), actor, command.expectedStatus(), command.reason());
            if (recovery.getStatus() == OrderCreationRecovery.Status.COMPLETED) return claimOf(recovery, false);
            requireNoOrder(orderId);
            if (!recovery.getId().equals(creation.getRecoveryId()) || creation.getStatus() != OrderCreationStatus.RECOVERY_REQUIRED) {
                throw conflict();
            }
            boolean execute = recovery.resume(command.resumeExecutionId());
            if (execute) event(recovery, "RESUMED", "운영자가 이전 실행 중단을 확인하고 실행권을 다시 획득했다.");
            return claimOf(recovery, execute);
        }
        if (command.resumeExecutionId() != null || creation.getRecoveryId() != null
                || creation.getStatus() != command.expectedStatus()
                || (creation.getStatus() != OrderCreationStatus.PROCESSING && creation.getStatus() != OrderCreationStatus.RECOVERY_REQUIRED)) {
            throw conflict();
        }
        requireNoOrder(orderId);
        String previous = "status=" + creation.getStatus() + ", failureReason=" + creation.getFailureReason()
                + ", deliveryId=" + creation.getDeliveryId();
        recovery = OrderCreationRecovery.start(command.recoveryId(), creation.getId(), orderId, actor,
                command.expectedStatus(), command.reason());
        creation.claimRecovery(recovery.getId());
        recoveries.saveAndFlush(recovery);
        event(recovery, "CLAIMED", previous);
        return claimOf(recovery, true);
    }

    public void record(UUID recoveryId, UUID executionId, String step, String detail) {
        OrderCreationRecovery recovery = recovery(recoveryId);
        recovery.validateExecution(executionId);
        event(recovery, step, detail);
    }

    /** CLOSED 확인 뒤에도 로컬 주문·실행권을 다시 검사한다. 원격 호출은 이 트랜잭션 밖이다. */
    public void finish(UUID orderId, UUID recoveryId, UUID executionId) {
        OrderCreation creation = creation(orderId);
        OrderCreationRecovery recovery = recovery(recoveryId);
        recovery.validateExecution(executionId);
        requireNoOrder(orderId);
        creation.finishRecovery(recoveryId);
        recovery.complete(executionId);
        event(recovery, "COMPLETED", "주문 없음·배송 CLOSED를 확인했다. 생성 시도를 ABORTED로 종료했다.");
    }

    public void defer(UUID recoveryId, UUID executionId, String detail) {
        OrderCreationRecovery recovery = recovery(recoveryId);
        // Note: 과거 작업자의 늦은 실패가 새 실행의 결과를 덮지 못한다.
        if (recovery.getStatus() != OrderCreationRecovery.Status.RUNNING || !recovery.getExecutionId().equals(executionId)) return;
        recovery.defer(executionId, detail);
        event(recovery, "DEFERRED", detail);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public OrderCreationRecoveryResult get(UUID recoveryId) {
        OrderCreationRecovery recovery = recoveries.findById(recoveryId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return OrderCreationRecoveryResult.from(recovery, events.findByRecoveryIdOrderByCreatedAtAsc(recoveryId));
    }

    private void requireNoOrder(UUID orderId) {
        if (orders.findByIdIncludingDeletedForUpdate(orderId).isPresent()) throw conflict();
    }

    private OrderCreation creation(UUID orderId) {
        return creations.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_CREATION_NOT_FOUND));
    }

    private OrderCreationRecovery recovery(UUID recoveryId) {
        return recoveries.findByIdForUpdate(recoveryId).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private void event(OrderCreationRecovery recovery, String step, String detail) {
        events.save(OrderCreationRecoveryEvent.record(recovery, step, detail));
    }

    private Claim claimOf(OrderCreationRecovery recovery, boolean execute) {
        return new Claim(recovery.getId(), recovery.getOrderId(), recovery.getExecutionId(), execute);
    }

    private BusinessException conflict() {
        return new BusinessException(CommonErrorCode.INVALID_STATUS_TRANSITION, "주문·시도·복구 상태가 요청과 일치하지 않습니다.");
    }
}
