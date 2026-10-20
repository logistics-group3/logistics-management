package com.sparta.logistics.order.application;

import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.order.application.dto.CompanyResult;
import com.sparta.logistics.order.application.dto.CreateDeliveryCommand;
import com.sparta.logistics.order.application.dto.CreateOrderCommand;
import com.sparta.logistics.order.application.dto.OrderCreationAttempt;
import com.sparta.logistics.order.application.dto.OrderCreationCommitResult;
import com.sparta.logistics.order.application.dto.OrderCreationOutcome;
import com.sparta.logistics.order.application.dto.OrderResult;
import com.sparta.logistics.order.application.dto.ProductResult;
import com.sparta.logistics.order.application.dto.UpdateOrderCommand;
import com.sparta.logistics.order.domain.CreationFailureReason;
import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.domain.OrderErrorCode;
import com.sparta.logistics.order.domain.OrderRepository;
import com.sparta.logistics.order.domain.OrderRequestIdentity;
import feign.FeignException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 쓰기 흐름. 생성의 외부 호출과 수정의 로컬 트랜잭션을 구분한다.
 *
 * <p>생성(O-01)은 두 단계다(이슈 #16). 접수 단계는 요청 스레드에서 멱등키 재확인·업체·상품 조회·권한 확인·진행 기록 시작까지만 하고
 * 바로 응답한다(accept). 처리 단계는 별도 스레드에서 I-09 배송 생성 → 주문 저장 → 실패 시 보상을 끝까지 하고 결과를 진행 기록에 남긴다(process).
 * create는 두 단계를 한 스레드에서 이어 실행한다(결과·예외를 호출자에게 그대로 돌려주는 동기 실행).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final int COMPENSATION_MAX_ATTEMPTS = 3;
    /**
     * 접수 단계 전체 마감. 업체·상품 조회 사이마다 확인해 넘으면 다음 호출 없이 503으로 끝낸다.
     * 브라우저 20초·Gateway 60초 안에 응답하도록 최악 소요를 이 마감 + 호출 1건(order-company 연결 1초·읽기 1.5초·최대 3회)으로 묶는다.
     */
    private static final Duration ACCEPT_BUDGET = Duration.ofSeconds(8);

    private final CompanyReader companyReader;
    private final DeliveryGateway deliveryGateway;
    private final OrderWriter orderWriter;
    private final OrderRepository orderRepository;
    private final OrderCreationExecutor creationExecutor;

    /**
     * O-01 비동기 접수. 요청업체 관리자 또는 MASTER만 주문한다. 접수까지 마치면 처리 단계를 별도 스레드에 맡기고 즉시 반환한다.
     * 같은 키 재요청은 처리 중이면 같은 orderId의 접수 결과, 완료면 최초 응답이다. 처리 결과는 진행 기록(O-07)으로 확인한다.
     * 실행기가 가득 차면 원격 호출 전이므로 시도를 ABORTED로 닫고 503이다.
     */
    @Transactional(propagation = Propagation.NEVER)
    public OrderCreationOutcome accept(UserContext user, String idempotencyKey, CreateOrderCommand command) {
        Started started = begin(user, idempotencyKey, command, true);
        if (started.replay() != null) return started.replay();
        Creation creation = started.creation();
        try {
            creationExecutor.submit(() -> run(creation));
        } catch (RejectedExecutionException rejected) {
            log.warn("주문 생성 실행기가 가득 찼다: orderId={}, attemptId={}", creation.attempt().orderId(), creation.attempt().attemptId());
            BusinessException unavailable = new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
            abortCreationSafely(creation.attempt(), null, unavailable);
            throw unavailable;
        }
        return OrderCreationOutcome.accepted(creation.attempt().orderId());
    }

    /**
     * O-01 동기 실행. 접수와 처리를 한 스레드에서 이어서 하고 결과나 원래 예외를 그대로 돌려준다.
     * 같은 키가 처리 중이면 409 ORDER_CREATION_IN_PROGRESS다. 요청 경로는 accept를 쓴다.
     */
    @Transactional(propagation = Propagation.NEVER)
    public OrderResult create(UserContext user, String idempotencyKey, CreateOrderCommand command) {
        Started started = begin(user, idempotencyKey, command, false);
        if (started.replay() != null) return started.replay().order();
        return process(started.creation());
    }

    /** 접수 단계. 같은 키 재요청 판정 → 업체·상품 조회·권한 → 진행 기록 시작. 처리 단계에 넘길 정보를 돌려준다. */
    private Started begin(UserContext user, String idempotencyKey, CreateOrderCommand command, boolean asyncAcceptance) {
        // Note: 원격 호출을 DB 트랜잭션 안에 두지 않는다. 저장은 OrderWriter의 독립 트랜잭션으로만 수행한다.
        OrderRequestIdentity identity = command.identity(idempotencyKey);
        Optional<OrderCreationOutcome> replay = findReplay(user, identity, asyncAcceptance);
        if (replay.isPresent()) return Started.replayed(replay.get());
        Deadline deadline = new Deadline(ACCEPT_BUDGET);
        OrderCreationAttempt attempt = new OrderCreationAttempt(UUID.randomUUID(), UUID.randomUUID(), user.userId());
        UUID orderId = attempt.orderId();
        CompanyResult supplier;
        CompanyResult receiver;
        ProductResult product;
        try {
            deadline.check();
            supplier = companyReader.getCompany(command.supplierCompanyId());
            deadline.check();
            receiver = companyReader.getCompany(command.receiverCompanyId());
            user.validateSelfOrMaster(receiver.managerId());
            deadline.check();
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
            return Started.replayed(findReplay(user, identity, asyncAcceptance)
                    .orElseThrow(() -> new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR)));
        } catch (RuntimeException exception) {
            log.error("주문 생성 시작 기록 실패: orderId={}, attemptId={}, cause={}",
                    orderId, attempt.attemptId(), exception.getClass().getSimpleName());
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        return Started.started(new Creation(attempt, user.userId(), command,
                new CreateDeliveryCommand(orderId, command.supplierCompanyId(), command.receiverCompanyId(), supplier.hubId(),
                        receiver.hubId(), supplier.address(), receiver.address(), receiver.name(), receiver.managerId()),
                product, supplier.name(), receiver.name()));
    }

    private Optional<OrderCreationOutcome> findReplay(UserContext user, OrderRequestIdentity identity, boolean asyncAcceptance) {
        return asyncAcceptance
                ? orderWriter.findAcceptance(user.userId(), identity)
                : orderWriter.findReplay(user.userId(), identity).map(OrderCreationOutcome::completed);
    }

    /** 처리 단계를 별도 스레드에서 실행한다. 모든 실패 경로가 진행 기록에 결과를 남기므로 여기서는 로그만 남기고 삼킨다. */
    private void run(Creation creation) {
        try {
            process(creation);
        } catch (RuntimeException exception) {
            log.warn("주문 생성 처리 종료(실패): orderId={}, attemptId={}, cause={}", creation.attempt().orderId(),
                    creation.attempt().attemptId(), exception.getClass().getSimpleName());
        }
    }

    /** 처리 단계. I-09 배송 생성 → 주문 저장 → 실패 시 보상. 결과나 원래 예외를 돌려주고, 진행 기록에도 남긴다. */
    private OrderResult process(Creation creation) {
        OrderCreationAttempt attempt = creation.attempt();
        UUID deliveryId = createDelivery(attempt, creation.deliveryCommand());
        try {
            CreateOrderCommand command = creation.command();
            Order order = Order.create(attempt.orderId(), creation.ordererId(), command.supplierCompanyId(),
                    command.receiverCompanyId(), command.productId(), creation.product().name(), creation.product().price(),
                    command.quantity(), deliveryId, command.requestNote());
            return orderWriter.save(order, creation.supplierName(), creation.receiverName());
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
                abortCreationSafely(attempt, null, exception);
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
        // Note: I-13 204만으로는 지연된 I-09가 뒤늦게 배송을 만들지 않는다고 단정할 수 없다. 보상이 성공한 뒤 I-17이
        // CLOSED(종료 표시 있음, 이후 I-09는 409로 거절)일 때만 시도를 닫고, 나머지는 확인 대상으로 남긴다.
        if (compensateDelivery(attempt) && isCreationClosed(attempt)) {
            abortCreationSafely(attempt, null, new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE));
            return;
        }
        markCreationRecoverySafely(attempt, null, CreationFailureReason.DELIVERY_RESULT_UNKNOWN, failure);
    }

    /** I-17 확인. 조회에 실패하면 닫을 근거가 없으므로 닫히지 않은 것으로 본다. */
    private boolean isCreationClosed(OrderCreationAttempt attempt) {
        try {
            return deliveryGateway.isCreationClosed(attempt.orderId());
        } catch (RuntimeException exception) {
            log.warn("배송 생성 시도 종료 확인 실패: orderId={}, attemptId={}, cause={}",
                    attempt.orderId(), attempt.attemptId(), exception.getClass().getSimpleName());
            return false;
        }
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
            abortCreationSafely(attempt, deliveryId, creationFailure(failure));
        } else {
            markCreationRecoverySafely(attempt, deliveryId, CreationFailureReason.DELIVERY_DELETE_FAILED, failure);
        }
        throw creationFailure(failure);
    }

    private BusinessException creationFailure(RuntimeException failure) {
        return failure instanceof BusinessException business ? business : new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    private void abortCreationSafely(OrderCreationAttempt attempt, UUID deliveryId, BusinessException cause) {
        try {
            orderWriter.abort(attempt, deliveryId, cause.getErrorCode().code(), cause.getMessage());
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

    /** 접수 단계의 결과: 같은 키 재요청의 응답이거나, 처리 단계에 넘길 정보다. */
    private record Started(OrderCreationOutcome replay, Creation creation) {

        static Started replayed(OrderCreationOutcome outcome) {
            return new Started(outcome, null);
        }

        static Started started(Creation creation) {
            return new Started(null, creation);
        }
    }

    /** 처리 단계에 필요한 모든 값. 접수 단계에서 조회한 업체·상품 정보를 다시 부르지 않고 그대로 쓴다. */
    private record Creation(OrderCreationAttempt attempt, UUID ordererId, CreateOrderCommand command,
                            CreateDeliveryCommand deliveryCommand, ProductResult product,
                            String supplierName, String receiverName) {
    }

    /** 접수 단계 마감. 넘기면 다음 원격 호출 없이 503으로 끝낸다. */
    private static final class Deadline {

        private final long expiresAtNanos;

        Deadline(Duration budget) {
            this.expiresAtNanos = System.nanoTime() + budget.toNanos();
        }

        void check() {
            if (System.nanoTime() - expiresAtNanos > 0) {
                throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
            }
        }
    }
}
