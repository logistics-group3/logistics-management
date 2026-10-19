package com.sparta.logistics.order.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.order.application.CompanyReader;
import com.sparta.logistics.order.application.DeliveryGateway;
import com.sparta.logistics.order.application.OrderNotificationSender;
import com.sparta.logistics.order.application.OrderService;
import com.sparta.logistics.order.application.OrderWriter;
import com.sparta.logistics.order.application.dto.CompanyResult;
import com.sparta.logistics.order.application.dto.CreateDeliveryCommand;
import com.sparta.logistics.order.application.dto.CreateOrderCommand;
import com.sparta.logistics.order.application.dto.OrderCreationAttempt;
import com.sparta.logistics.order.application.dto.OrderCreationCommitResult;
import com.sparta.logistics.order.application.dto.OrderResult;
import com.sparta.logistics.order.application.dto.ProductResult;
import com.sparta.logistics.order.application.event.OrderCreatedEvent;
import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.domain.OrderCreation;
import com.sparta.logistics.order.domain.OrderCreationRepository;
import com.sparta.logistics.order.domain.OrderRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 실제 DB 커밋 뒤 반환 경계 예외와 재확인 잠금을 검증한다. 네트워크 패킷 유실을 주입하는 테스트는 아니다. */
@Import(OrderCreationRecordIntegrationTest.FaultConfiguration.class)
@TestPropertySource(properties = {
        "spring.flyway.schemas=order_schema", "spring.jpa.properties.hibernate.default_schema=order_schema",
        "spring.jpa.open-in-view=false", "slack.webhook-url="
})
class OrderCreationRecordIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private OrderService service;
    @Autowired
    private OrderWriter writer;
    @Autowired
    private OrderRepository orders;
    @Autowired
    private OrderCreationRepository creations;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private Faults faults;
    @MockitoBean
    private CompanyReader company;
    @MockitoBean
    private DeliveryGateway delivery;
    @MockitoBean
    private OrderNotificationSender notification;

    private final UserContext user = new UserContext(UUID.randomUUID(), "owner", Role.USER, false);
    private final UUID supplierId = UUID.randomUUID();
    private final UUID receiverId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID deliveryId = UUID.randomUUID();
    private final CreateOrderCommand command = new CreateOrderCommand(supplierId, receiverId, productId, 2, "오후 도착 희망");
    private final List<UUID> fixtureIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        faults.afterCommit.set(false);
        faults.verification.set(false);
        faults.event.set(false);
        fixtureIds.forEach(id -> {
            jdbc.update("DELETE FROM order_schema.p_order_creations WHERE order_id=?", id);
            jdbc.update("DELETE FROM order_schema.p_orders WHERE order_id=?", id);
        });
    }

    @Test
    @DisplayName("주문 행 없이 시작 기록을 커밋하고 실제 배송 호출은 DB 트랜잭션 밖에서 수행한다")
    void recordsBeforeRemoteMutation() {
        stubExternalServices();

        OrderResult result = service.create(user, "test-key", command);

        assertThat(recordStatus(result.orderId())).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT delivery_id FROM order_schema.p_order_creations WHERE order_id=?", UUID.class, result.orderId()))
                .isEqualTo(deliveryId);
        verify(notification).send(any());
    }

    @Test
    @DisplayName("실제 커밋 뒤 반환 경계가 실패해도 저장된 주문을 반환하고 삭제·알림 중복을 막는다")
    void recoversConfirmedCommitAfterReturnFailure() {
        stubExternalServices();
        faults.afterCommit.set(true);

        OrderResult result = service.create(user, "test-key", command);

        assertThat(orders.findById(result.orderId())).isPresent();
        assertThat(recordStatus(result.orderId())).isEqualTo("COMPLETED");
        verify(delivery, never()).deleteByOrderId(any());
        verify(notification, times(1)).send(any());
    }

    @Test
    @DisplayName("커밋 후 예외와 재확인 실패가 겹쳐도 정상 주문의 배송·완료 기록을 변경하지 않는다")
    void neverDeletesCommittedOrderWhenVerificationFails() {
        stubExternalServices();
        faults.afterCommit.set(true);
        faults.verification.set(true);

        assertThatThrownBy(() -> service.create(user, "test-key", command)).extracting("errorCode").isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR);

        UUID orderId = fixtureIds.get(0);
        assertThat(orders.findById(orderId)).isPresent();
        assertThat(recordStatus(orderId)).isEqualTo("COMPLETED");
        verify(delivery, never()).deleteByOrderId(any());
        verify(notification, times(1)).send(any());
    }

    @Test
    @DisplayName("이벤트 발행 중 실패하면 주문과 완료 기록을 롤백하고 배송 정리 후 종료한다")
    void rollsBackOrderAndCompletionTogether() {
        stubExternalServices();
        faults.event.set(true);
        willAnswer(invocation -> {
            UUID orderId = invocation.getArgument(0);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(orders.findById(orderId)).isEmpty();
            assertThat(recordStatus(orderId)).isEqualTo("PROCESSING");
            return null;
        }).given(delivery).deleteByOrderId(any());

        assertThatThrownBy(() -> service.create(user, "test-key", command)).extracting("errorCode").isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR);

        assertThat(recordStatus(fixtureIds.get(0))).isEqualTo("ABORTED");
        verifyNoInteractions(notification);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("재확인은 진행 기록 잠금 해제까지 기다리고 실제 커밋·롤백 결과를 읽는다")
    void waitsForOriginalTransaction(boolean rollback) throws Exception {
        OrderCreationAttempt attempt = start();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger blockerPid = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> saving = executor.submit(() -> transactions.executeWithoutResult(status -> {
                OrderCreation creation = creations.findByOrderIdForUpdate(attempt.orderId()).orElseThrow();
                blockerPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                locked.countDown();
                try { assertThat(release.await(10, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
                Order saved = orders.saveAndFlush(order(attempt.orderId()));
                creation.completeWithSnapshot(saved);
                if (rollback) status.setRollbackOnly();
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<OrderCreationCommitResult> checking = executor.submit(() -> writer.resolveCreation(attempt));
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))",
                            Long.class, blockerPid.get())).isGreaterThan(0));
            release.countDown();
            saving.get(10, TimeUnit.SECONDS);

            assertThat(checking.get(10, TimeUnit.SECONDS).outcome()).isEqualTo(rollback
                    ? OrderCreationCommitResult.Outcome.NOT_COMMITTED : OrderCreationCommitResult.Outcome.COMMITTED);
        } finally {
            release.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("종료한 생성 기록도 같은 orderId로 다시 만들 수 없다")
    void keepsOrderIdentityUniqueAfterAbort() {
        OrderCreationAttempt attempt = start();
        writer.abort(attempt, null, null, null);

        assertThatThrownBy(() -> writer.start(new OrderCreationAttempt(UUID.randomUUID(), attempt.orderId(), user.userId()), command.identity("different-key")))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uk_p_order_creations_order_id");
        assertThat(recordStatus(attempt.orderId())).isEqualTo("ABORTED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"status='BROKEN'", "status='RECOVERY_REQUIRED'", "status='COMPLETED'",
            "failure_reason='BAD_REASON'", "failure_reason='DELIVERY_DELETE_FAILED'"})
    @DisplayName("DB CHECK가 잘못된 생성 상태·완료 배송 누락·복구 사유 조합을 막는다")
    void validatesDatabaseState(String assignment) {
        OrderCreationAttempt attempt = start();

        assertThatThrownBy(() -> jdbc.update("UPDATE order_schema.p_order_creations SET " + assignment + " WHERE order_id=?", attempt.orderId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("주문이 있어도 진행 기록이 완료가 아니면 저장 성공으로 단정하지 않는다")
    void treatsInconsistentStateAsUnknown() {
        OrderCreationAttempt attempt = start();
        orders.saveAndFlush(order(attempt.orderId()));

        assertThat(writer.resolveCreation(attempt).outcome()).isEqualTo(OrderCreationCommitResult.Outcome.UNKNOWN);
    }

    @Test
    @DisplayName("다른 생성 시도의 ID로 종료 기록을 바꾸지 않는다")
    void rejectsMismatchedAttempt() {
        OrderCreationAttempt attempt = start();

        assertThatThrownBy(() -> writer.abort(new OrderCreationAttempt(UUID.randomUUID(), attempt.orderId(), user.userId()), null, null, null))
                .extracting("errorCode").isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR);
        assertThat(recordStatus(attempt.orderId())).isEqualTo("PROCESSING");
    }

    @Test
    @DisplayName("V3은 주문 없는 기록을 허용하고 스키마·주문 ID 유니크·복구 조회 인덱스를 적용한다")
    void migratesProgressSchema() {
        OrderCreationAttempt attempt = start();
        assertThat(orders.findById(attempt.orderId())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_schema.flyway_schema_history WHERE version='3' AND success", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT to_regclass('public.p_order_creations')::text", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE conrelid='order_schema.p_order_creations'::regclass AND contype='f'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE schemaname='order_schema' AND indexname='idx_p_order_creations_unresolved_created_at'", String.class))
                .contains("deleted_at IS NULL", "PROCESSING", "RECOVERY_REQUIRED");
    }

    private OrderCreationAttempt start() {
        OrderCreationAttempt attempt = new OrderCreationAttempt(UUID.randomUUID(), UUID.randomUUID(), user.userId());
        fixtureIds.add(attempt.orderId());
        writer.start(attempt, command.identity("test-key"));
        return attempt;
    }

    private void stubExternalServices() {
        given(company.getCompany(supplierId)).willReturn(new CompanyResult(supplierId, "공급", UUID.randomUUID(), "서울", UUID.randomUUID()));
        given(company.getCompany(receiverId)).willReturn(new CompanyResult(receiverId, "수령", UUID.randomUUID(), "부산", user.userId()));
        given(company.getProduct(productId)).willReturn(new ProductResult(productId, supplierId, "상품", 10_000L));
        given(delivery.create(any())).willAnswer(invocation -> {
            CreateDeliveryCommand command = invocation.getArgument(0);
            fixtureIds.add(command.orderId());
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(recordStatus(command.orderId())).isEqualTo("PROCESSING");
            assertThat(orders.findById(command.orderId())).isEmpty();
            return deliveryId;
        });
    }

    private Order order(UUID orderId) {
        return Order.create(orderId, user.userId(), supplierId, receiverId, productId, "상품", 10_000L, 2, deliveryId, "오후 도착 희망");
    }

    private String recordStatus(UUID orderId) {
        return jdbc.queryForObject("SELECT status FROM order_schema.p_order_creations WHERE order_id=?", String.class, orderId);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    static class FaultConfiguration {
        @Bean Faults creationFaults() { return new Faults(); }
    }

    @Aspect
    @org.springframework.core.annotation.Order(Ordered.HIGHEST_PRECEDENCE)
    static class Faults {
        final AtomicBoolean afterCommit = new AtomicBoolean();
        final AtomicBoolean verification = new AtomicBoolean();
        final AtomicBoolean event = new AtomicBoolean();

        @Around("execution(* com.sparta.logistics.order.application.OrderWriter.save(..))")
        Object afterCommittedSave(ProceedingJoinPoint call) throws Throwable {
            Object result = call.proceed();
            if (afterCommit.compareAndSet(true, false)) {
                // Note: 트랜잭션 advice 바깥에서 예외를 주입해 실제 커밋 뒤의 반환 실패를 재현한다.
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                throw new DataAccessResourceFailureException("injected after actual commit");
            }
            return result;
        }

        @Around("execution(* com.sparta.logistics.order.application.OrderWriter.resolveCreation(..))")
        Object failVerification(ProceedingJoinPoint call) throws Throwable {
            if (verification.get()) throw new DataAccessResourceFailureException("injected verification failure");
            return call.proceed();
        }

        @EventListener
        void failEvent(OrderCreatedEvent ignored) {
            if (event.get()) throw new DataAccessResourceFailureException("injected event publication failure");
        }
    }
}
