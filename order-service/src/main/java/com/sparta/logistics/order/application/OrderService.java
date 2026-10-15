package com.sparta.logistics.order.application;

import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.order.application.dto.CompanyResult;
import com.sparta.logistics.order.application.dto.CreateDeliveryCommand;
import com.sparta.logistics.order.application.dto.CreateOrderCommand;
import com.sparta.logistics.order.application.dto.OrderCreationAttempt;
import com.sparta.logistics.order.application.dto.OrderCreationCommitResult;
import com.sparta.logistics.order.application.dto.OrderResult;
import com.sparta.logistics.order.application.dto.ProductResult;
import com.sparta.logistics.order.application.dto.UpdateOrderCommand;
import com.sparta.logistics.order.domain.CreationFailureReason;
import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.domain.OrderErrorCode;
import com.sparta.logistics.order.domain.OrderRepository;
import com.sparta.logistics.order.domain.OrderRequestIdentity;
import feign.FeignException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 주문 쓰기 흐름. 생성의 외부 호출과 수정의 로컬 트랜잭션을 구분한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final int COMPENSATION_MAX_ATTEMPTS = 3;

    private final CompanyReader companyReader;
    private final DeliveryGateway deliveryGateway;
    private final OrderWriter orderWriter;
    private final OrderRepository orderRepository;

    /** O-01. 요청업체 관리자 또는 MASTER만 주문하며 배송 생성·주문 저장 실패를 단계별로 보상한다. */
    @Transactional(propagation = Propagation.NEVER)
    public OrderResult create(UserContext user, String idempotencyKey, CreateOrderCommand command) {
        // Note: 원격 호출을 DB 트랜잭션 안에 두지 않는다. 저장은 OrderWriter의 독립 트랜잭션으로만 수행한다.
        OrderRequestIdentity identity = command.identity(idempotencyKey);
        Optional<OrderResult> replay = orderWriter.findReplay(user.userId(), identity);
        if (replay.isPresent()) return replay.get();
        OrderCreationAttempt attempt = new OrderCreationAttempt(UUID.randomUUID(), UUID.randomUUID(), user.userId());
        UUID orderId = attempt.orderId();
        CompanyResult supplier;
        CompanyResult receiver;
        ProductResult product;
        try {
            supplier = companyReader.getCompany(command.supplierCompanyId());
            receiver = companyReader.getCompany(command.receiverCompanyId());
            user.validateSelfOrMaster(receiver.managerId());
            product = companyReader.getProduct(command.productId());
        } catch (FeignException exception) {
            log.warn("업체·상품 조회 통신 실패: orderId={}", orderId, exception);
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }
        if (!command.supplierCompanyId().equals(product.companyId())) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "상품이 공급업체 소속이 아닙니다.");
        }

        try {
            orderWriter.start(attempt, identity);
        } catch (DataIntegrityViolationException conflict) {
            // Note: 동시 INSERT의 패자는 이미 롤백된 뒤 새 트랜잭션에서 승자의 기록을 읽는다. 원격 배송 생성은 한 요청만 한다.
            return orderWriter.findReplay(user.userId(), identity)
                    .orElseThrow(() -> new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR));
        } catch (RuntimeException exception) {
            log.error("주문 생성 시작 기록 실패: orderId={}, attemptId={}, cause={}",
                    orderId, attempt.attemptId(), exception.getClass().getSimpleName());
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        UUID deliveryId = createDelivery(attempt, new CreateDeliveryCommand(orderId, command.supplierCompanyId(),
                command.receiverCompanyId(), supplier.hubId(), receiver.hubId(), supplier.address(), receiver.address(),
                receiver.name(), receiver.managerId()));
        try {
            Order order = Order.create(orderId, user.userId(), command.supplierCompanyId(), command.receiverCompanyId(),
                    command.productId(), product.name(), product.price(), command.quantity(), deliveryId, command.requestNote());
            return orderWriter.save(order, supplier.name(), receiver.name());
        } catch (RuntimeException exception) {
            return handleCreationFailure(attempt, deliveryId, exception);
        }
    }

    /** O-04. 주문자 본인 또는 MASTER만 요청사항을 수정하며 취소된 주문은 거부한다. */
    @Transactional
    public OrderResult updateRequestNote(UserContext user, UUID orderId, UpdateOrderCommand command) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        user.validateSelfOrMaster(order.getOrdererId());
        order.updateRequestNote(command.requestNote());
        // Note: 변경 감지와 Audit 갱신을 먼저 실행해 응답에도 이번 수정의 updatedAt을 담는다. 커밋은 메서드 종료 후다.
        orderRepository.flush();
        return OrderResult.from(order);
    }

    private UUID createDelivery(OrderCreationAttempt attempt, CreateDeliveryCommand command) {
        try {
            UUID deliveryId = deliveryGateway.create(command);
            if (deliveryId == null) {
                throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "배송 생성 응답에 ID가 없습니다.");
            }
            return deliveryId;
        } catch (BusinessException exception) {
            if (exception.getErrorCode().status().is4xxClientError()) {
                abortCreationSafely(attempt, null);
                throw exception;
            }
            handleUnknownDelivery(attempt, exception);
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        } catch (RuntimeException exception) {
            handleUnknownDelivery(attempt, exception);
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    private void handleUnknownDelivery(OrderCreationAttempt attempt, RuntimeException failure) {
        // Note: SA 7.1의 삭제 보상은 시도하지만 204 뒤 지연 생성이 끝났다고 단정하지 않는다. 확인 대상 기록을 남긴다.
        compensateDelivery(attempt);
        markCreationRecoverySafely(attempt, null, CreationFailureReason.DELIVERY_RESULT_UNKNOWN, failure);
    }

    private OrderResult handleCreationFailure(OrderCreationAttempt attempt, UUID deliveryId, RuntimeException failure) {
        OrderCreationCommitResult result;
        try {
            result = orderWriter.resolveCreation(attempt);
        } catch (RuntimeException verificationFailure) {
            markCreationRecoverySafely(attempt, deliveryId, CreationFailureReason.ORDER_COMMIT_UNKNOWN, verificationFailure);
            throw creationFailure(failure);
        }
        if (result.outcome() == OrderCreationCommitResult.Outcome.COMMITTED) {
            log.warn("주문 생성 커밋 재확인: orderId={}, attemptId={}", attempt.orderId(), attempt.attemptId());
            return result.order();
        }
        if (result.outcome() != OrderCreationCommitResult.Outcome.NOT_COMMITTED) {
            markCreationRecoverySafely(attempt, deliveryId, CreationFailureReason.ORDER_COMMIT_UNKNOWN, failure);
            throw creationFailure(failure);
        }

        log.error("주문 미저장 확인: orderId={}, attemptId={}, cause={}",
                attempt.orderId(), attempt.attemptId(), failure.getClass().getSimpleName());
        if (compensateDelivery(attempt)) {
            abortCreationSafely(attempt, deliveryId);
        } else {
            markCreationRecoverySafely(attempt, deliveryId, CreationFailureReason.DELIVERY_DELETE_FAILED, failure);
        }
        throw creationFailure(failure);
    }

    private BusinessException creationFailure(RuntimeException failure) {
        return failure instanceof BusinessException business ? business : new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    private void abortCreationSafely(OrderCreationAttempt attempt, UUID deliveryId) {
        try {
            orderWriter.abort(attempt, deliveryId);
        } catch (RuntimeException exception) {
            log.error("주문 생성 종료 기록 실패: orderId={}, attemptId={}, cause={}",
                    attempt.orderId(), attempt.attemptId(), exception.getClass().getSimpleName());
        }
    }

    private void markCreationRecoverySafely(OrderCreationAttempt attempt, UUID deliveryId,
            CreationFailureReason reason, RuntimeException failure) {
        log.error("주문 생성 복구 필요: orderId={}, attemptId={}, reason={}, cause={}",
                attempt.orderId(), attempt.attemptId(), reason, failure.getClass().getSimpleName());
        try {
            orderWriter.requireRecovery(attempt, deliveryId, reason);
        } catch (RuntimeException recordFailure) {
            // Note: 진행 기록 갱신 실패가 최초 오류를 덮지 않게 한다. 이미 COMPLETED인 기록도 변경하지 않는다.
            log.error("주문 생성 복구 기록 실패: orderId={}, attemptId={}, reason={}, cause={}",
                    attempt.orderId(), attempt.attemptId(), reason, recordFailure.getClass().getSimpleName());
        }
    }

    private boolean compensateDelivery(OrderCreationAttempt attempt) {
        RuntimeException lastFailure = null;
        for (int number = 1; number <= COMPENSATION_MAX_ATTEMPTS; number++) {
            try {
                deliveryGateway.deleteByOrderId(attempt.orderId());
                return true;
            } catch (RuntimeException exception) {
                lastFailure = exception;
                log.warn("배송 삭제 보상 시도 실패: orderId={}, attemptId={}, number={}", attempt.orderId(), attempt.attemptId(), number);
            }
        }
        // Note: 보상 실패가 최초 실패 원인을 덮지 않게 한다. SA 7.1에 따라 orderId를 남겨 수동 재처리한다.
        log.error("배송 삭제 보상 실패: orderId={}, attemptId={}, status=COMPENSATION_FAILED, attempts={}, cause={}",
                attempt.orderId(), attempt.attemptId(), COMPENSATION_MAX_ATTEMPTS, lastFailure.getClass().getSimpleName());
        return false;
    }
}
