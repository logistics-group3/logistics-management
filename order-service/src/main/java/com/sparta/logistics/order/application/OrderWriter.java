package com.sparta.logistics.order.application;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.order.application.dto.OrderCreationAttempt;
import com.sparta.logistics.order.application.dto.OrderCreationCommitResult;
import com.sparta.logistics.order.application.dto.OrderCreationOutcome;
import com.sparta.logistics.order.application.dto.OrderResult;
import com.sparta.logistics.order.application.event.OrderCreatedEvent;
import com.sparta.logistics.order.domain.CreationFailureReason;
import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.domain.OrderCreation;
import com.sparta.logistics.order.domain.OrderCreationRepository;
import com.sparta.logistics.order.domain.OrderCreationStatus;
import com.sparta.logistics.order.domain.OrderRepository;
import com.sparta.logistics.order.domain.OrderRequestIdentity;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderWriter {

    private final OrderRepository orderRepository;
    private final OrderCreationRepository creationRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** O-01. 첫 원격 변경 전에 생성 진행 기록을 별도 커밋한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void start(OrderCreationAttempt attempt, OrderRequestIdentity identity) {
        creationRepository.saveAndFlush(OrderCreation.start(attempt.attemptId(), attempt.orderId(), attempt.ordererId(), identity));
    }

    /** O-01. 같은 사용자의 키만 조회하며 완료 응답은 현재 주문 대신 최초 스냅샷으로 반환한다. */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<OrderResult> findReplay(UUID userId, OrderRequestIdentity identity) {
        return creationRepository.findByKeyIncludingDeleted(userId, identity.key())
                .map(creation -> OrderResult.from(creation.replay(identity)));
    }

    /**
     * O-01 비동기 접수의 같은 키 재요청 판정. 처리 중이면 접수 결과(202)를, 완료면 최초 스냅샷(201)을 돌려준다.
     * 키·요청 충돌, 실패 종료, 복구 필요, 삭제된 기록은 동기 실행과 같은 409 예외다.
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<OrderCreationOutcome> findAcceptance(UUID userId, OrderRequestIdentity identity) {
        return creationRepository.findByKeyIncludingDeleted(userId, identity.key()).map(creation -> {
            creation.validateReplayOf(identity);
            if (creation.getStatus() == OrderCreationStatus.PROCESSING) {
                return OrderCreationOutcome.accepted(creation.getOrderId());
            }
            return OrderCreationOutcome.completed(OrderResult.from(creation.replay(identity)));
        });
    }

    /**
     * O-01 주문의 로컬 저장. 원격 호출을 포함하지 않는다.
     * Note: 별도 빈의 트랜잭션 프록시가 커밋까지 끝낸 뒤 반환하므로 호출자가 커밋 예외를 잡고 실제 결과를 다시 확인할 수 있다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderResult save(Order order, String supplierCompanyName, String receiverCompanyName) {
        OrderCreation creation = findCreation(order.getId());
        creation.validateProcessing();
        if (!creation.getOrdererId().equals(order.getOrdererId())) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
        Order savedOrder = orderRepository.saveAndFlush(order);
        creation.completeWithSnapshot(savedOrder);
        // Note: flush는 커밋이 아니다. 트랜잭션 안에서 발행해 커밋 성공 시에만 리스너가 실행되게 한다.
        eventPublisher.publishEvent(OrderCreatedEvent.from(savedOrder, supplierCompanyName, receiverCompanyName));
        return OrderResult.from(savedOrder);
    }

    /** O-01. 진행 기록 잠금 획득 후 같은 DB에서 주문과 완료 기록을 함께 확인한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderCreationCommitResult resolveCreation(OrderCreationAttempt attempt) {
        OrderCreation creation = findCreation(attempt);
        Order order = orderRepository.findByIdForUpdate(attempt.orderId()).orElse(null);
        if (creation.getStatus() == OrderCreationStatus.COMPLETED && order != null
                && order.getOrdererId().equals(creation.getOrdererId())
                && order.getDeliveryId().equals(creation.getDeliveryId())) {
            return creation.getResponseSnapshot() == null ? OrderCreationCommitResult.from(order)
                    : OrderCreationCommitResult.from(creation.getResponseSnapshot());
        }
        if (creation.getStatus() == OrderCreationStatus.PROCESSING && order == null) {
            return OrderCreationCommitResult.notCommitted();
        }
        return OrderCreationCommitResult.unknown();
    }

    /** O-01. 명확한 거절·확인된 배송 정리 후에만 생성 시도를 종료한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void abort(OrderCreationAttempt attempt, UUID deliveryId, String failureCode, String failureMessage) {
        findCreation(attempt).abort(deliveryId, failureCode, failureMessage);
    }

    /** O-01. 미확정 결과나 삭제 보상 실패는 주문이 없어도 추적할 수 있게 남긴다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requireRecovery(OrderCreationAttempt attempt, UUID deliveryId, CreationFailureReason reason) {
        findCreation(attempt).requireRecovery(deliveryId, reason);
    }

    /** 정상 작업이 복구로 인계됐는지 확인한다. 저장 시에는 같은 생성 기록 잠금으로 다시 검사한다. */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public boolean isRecoveryClaimed(OrderCreationAttempt attempt) {
        return creationRepository.findByOrderId(attempt.orderId())
                .filter(creation -> creation.getId().equals(attempt.attemptId()))
                .map(creation -> creation.getRecoveryId() != null).orElse(false);
    }

    private OrderCreation findCreation(UUID orderId) {
        return creationRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR));
    }

    private OrderCreation findCreation(OrderCreationAttempt attempt) {
        OrderCreation creation = findCreation(attempt.orderId());
        if (!creation.getId().equals(attempt.attemptId()) || !creation.getOrdererId().equals(attempt.ordererId())) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
        return creation;
    }
}
