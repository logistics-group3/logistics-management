package com.sparta.logistics.order.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.order.application.CompanyReader;
import com.sparta.logistics.order.application.DeliveryGateway;
import com.sparta.logistics.order.application.OrderCancellationService;
import com.sparta.logistics.order.application.OrderCancellationWriter;
import com.sparta.logistics.order.application.OrderNotificationSender;
import com.sparta.logistics.order.application.OrderService;
import com.sparta.logistics.order.application.dto.CancelOrderResult;
import com.sparta.logistics.order.application.dto.CancellationAttempt;
import com.sparta.logistics.order.application.dto.CancellationCompletionOutcome;
import com.sparta.logistics.order.application.dto.UpdateOrderCommand;
import com.sparta.logistics.order.domain.CancellationFailureReason;
import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.domain.OrderCancellation;
import com.sparta.logistics.order.domain.OrderCancellationRepository;
import com.sparta.logistics.order.domain.OrderRepository;
import com.sparta.logistics.order.domain.OrderStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 외부 포트만 대체해 실제 커밋·롤백·부분 유니크·동시 실행을 검증한다. */
@TestPropertySource(properties = {
        "spring.flyway.schemas=order_schema", "spring.jpa.properties.hibernate.default_schema=order_schema",
        "spring.jpa.open-in-view=false", "slack.webhook-url="
})
class OrderCancellationIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private OrderCancellationService service;
    @Autowired
    private OrderCancellationWriter writer;
    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderRepository orders;
    @Autowired
    private OrderCancellationRepository cancellations;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transactions;
    @MockitoBean
    private DeliveryGateway delivery;
    @MockitoBean
    private CompanyReader company;
    @MockitoBean
    private OrderNotificationSender notification;

    private final UserContext owner = new UserContext(UUID.randomUUID(), "owner", Role.USER, false);
    private final List<UUID> fixtureIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        try {
            verifyNoInteractions(company, notification);
        } finally {
            fixtureIds.forEach(id -> {
                jdbc.update("DELETE FROM order_schema.p_order_cancellations WHERE order_id=?", id);
                jdbc.update("DELETE FROM order_schema.p_orders WHERE order_id=?", id);
            });
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "MASTER"})
    @DisplayName("주문자와 MASTER는 트랜잭션 밖에서 배송을 취소하고 주문·진행 기록을 함께 커밋한다")
    void commitsCancellation(String role) {
        Order order = seed();
        willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(recordStatus(order.getId())).isEqualTo("PROCESSING");
            return null;
        }).given(delivery).cancel(order.getDeliveryId());

        CancelOrderResult result = service.cancel(role.equals("USER") ? owner
                : new UserContext(UUID.randomUUID(), "master", Role.MASTER, false), order.getId());

        assertThat(result).isEqualTo(new CancelOrderResult(order.getId(), OrderStatus.CANCELED));
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(recordStatus(order.getId())).isEqualTo("COMPLETED");
        assertThat(writer.resolveCompletion(attempt(order))).isEqualTo(CancellationCompletionOutcome.COMMITTED);
    }

    @Test
    @DisplayName("주문 UPDATE가 flush에서 실패하면 롤백 확인 후 트랜잭션 밖에서 배송을 원복한다")
    void compensatesFlushFailure() {
        Order order = seed();
        jdbc.execute("ALTER TABLE order_schema.p_orders ADD CONSTRAINT ck_test_cancel_failure CHECK (order_id <> '"
                + order.getId() + "'::uuid OR status <> 'CANCELED')");
        try {
            assertRolledBackBeforeCompensation(order);

            assertLocalFailure(order);

            assertThat(recordStatus(order.getId())).isEqualTo("ABORTED");
            verify(delivery).revertCancellation(order.getDeliveryId());
        } finally {
            jdbc.execute("ALTER TABLE order_schema.p_orders DROP CONSTRAINT ck_test_cancel_failure");
        }
    }

    @Test
    @DisplayName("flush가 성공해도 지연 트리거로 커밋이 실패하면 주문과 완료 기록을 함께 롤백하고 원복한다")
    void compensatesCommitFailure() {
        Order order = seed();
        jdbc.execute("""
                CREATE FUNCTION order_schema.fail_test_cancel_commit() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.order_id = '%s'::uuid AND NEW.status = 'CANCELED' THEN
                        RAISE EXCEPTION 'injected cancellation commit failure';
                    END IF;
                    RETURN NEW;
                END $$
                """.formatted(order.getId()));
        jdbc.execute("""
                CREATE CONSTRAINT TRIGGER test_cancel_commit_failure AFTER UPDATE ON order_schema.p_orders
                DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION order_schema.fail_test_cancel_commit()
                """);
        try {
            assertRolledBackBeforeCompensation(order);

            assertLocalFailure(order);

            assertThat(recordStatus(order.getId())).isEqualTo("ABORTED");
            verify(delivery).revertCancellation(order.getDeliveryId());
        } finally {
            jdbc.execute("DROP TRIGGER test_cancel_commit_failure ON order_schema.p_orders");
            jdbc.execute("DROP FUNCTION order_schema.fail_test_cancel_commit()");
        }
    }

    @Test
    @DisplayName("원복 실패는 복구 기록을 남기고 같은 주문의 새 취소를 막는다")
    void blocksAfterFailedCompensation() {
        Order order = seed();
        jdbc.execute("ALTER TABLE order_schema.p_orders ADD CONSTRAINT ck_test_cancel_failure CHECK (order_id <> '"
                + order.getId() + "'::uuid OR status <> 'CANCELED')");
        willThrow(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE)).given(delivery).revertCancellation(order.getDeliveryId());
        try {
            assertLocalFailure(order);
            assertThat(recordStatus(order.getId())).isEqualTo("RECOVERY_REQUIRED");
            assertThat(jdbc.queryForObject("SELECT failure_reason FROM order_schema.p_order_cancellations WHERE order_id=?",
                    String.class, order.getId())).isEqualTo("REVERT_FAILED");
            assertThatThrownBy(() -> service.cancel(owner, order.getId())).extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_STATUS_TRANSITION);
            verify(delivery).cancel(order.getDeliveryId());
        } finally {
            jdbc.execute("ALTER TABLE order_schema.p_orders DROP CONSTRAINT ck_test_cancel_failure");
        }
    }

    @Test
    @DisplayName("배송 대기 중 같은 주문 취소는 거부하고 다른 주문 취소와 요청사항 수정은 진행한다")
    void serializesSameOrderWithoutHoldingDatabaseTransaction() throws Exception {
        Order first = seed();
        Order second = seed();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            if (invocation.getArgument(0).equals(first.getDeliveryId())) {
                entered.countDown();
                assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return null;
        }).given(delivery).cancel(any());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<CancelOrderResult> firstResult = executor.submit(() -> service.cancel(owner, first.getId()));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> service.cancel(owner, first.getId())).extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_STATUS_TRANSITION);
            assertThat(service.cancel(owner, second.getId()).status()).isEqualTo(OrderStatus.CANCELED);
            orderService.updateRequestNote(owner, first.getId(), new UpdateOrderCommand("대기 중 변경한 요청사항"));
            release.countDown();

            assertThat(firstResult.get(10, TimeUnit.SECONDS).status()).isEqualTo(OrderStatus.CANCELED);
            assertThat(orders.findById(first.getId()).orElseThrow().getRequestNote()).isEqualTo("대기 중 변경한 요청사항");
            verify(delivery).cancel(first.getDeliveryId());
            verify(delivery).cancel(second.getDeliveryId());
        } finally {
            release.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("이전 트랜잭션의 진행 중·복구 대기 기록은 새 취소를 막는다")
    void retainsExecutionGuardAcrossTransactions(boolean recovery) {
        Order order = seed();
        CancellationAttempt attempt = writer.start(owner, order.getId(), UUID.randomUUID());
        if (recovery) writer.requireRecovery(attempt, CancellationFailureReason.DELIVERY_RESULT_UNKNOWN);

        assertThatThrownBy(() -> service.cancel(owner, order.getId())).extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_STATUS_TRANSITION);
        verifyNoInteractions(delivery);
    }

    @Test
    @DisplayName("확실히 중단한 시도는 기록을 보존하면서 새로운 취소를 허용한다")
    void allowsNewAttemptAfterAbort() {
        Order order = seed();
        CancellationAttempt first = writer.start(owner, order.getId(), UUID.randomUUID());
        writer.abort(first);

        assertThat(service.cancel(owner, order.getId()).status()).isEqualTo(OrderStatus.CANCELED);
        assertThat(jdbc.queryForList("SELECT status FROM order_schema.p_order_cancellations WHERE order_id=?", String.class, order.getId()))
                .containsExactlyInAnyOrder("ABORTED", "COMPLETED");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("부분 유니크 인덱스가 같은 주문의 미해결 시도 두 개를 DB에서도 거부한다")
    void enforcesUniqueUnresolvedAttempt(boolean recovery) {
        Order order = seed();
        OrderCancellation first = OrderCancellation.start(UUID.randomUUID(), order.getId(), order.getDeliveryId());
        if (recovery) first.requireRecovery(CancellationFailureReason.REVERT_FAILED);
        cancellations.saveAndFlush(first);

        assertThatThrownBy(() -> cancellations.saveAndFlush(OrderCancellation.start(UUID.randomUUID(), order.getId(), order.getDeliveryId())))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uk_p_order_cancellations_unresolved_order");
    }

    @ParameterizedTest
    @ValueSource(strings = {"status='BROKEN'", "status='RECOVERY_REQUIRED'", "failure_reason='BAD_REASON'", "failure_reason='REVERT_FAILED'"})
    @DisplayName("DB CHECK가 잘못된 취소 상태와 복구 사유 조합을 막는다")
    void enforcesStatusAndReasonChecks(String assignment) {
        Order order = seed();
        CancellationAttempt attempt = writer.start(owner, order.getId(), UUID.randomUUID());

        assertThatThrownBy(() -> jdbc.update("UPDATE order_schema.p_order_cancellations SET " + assignment + " WHERE order_cancellation_id=?", attempt.attemptId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("서로 맞지 않는 주문·진행 상태는 롤백으로 단정하지 않는다")
    void reportsConflictingCompletionAsUnknown() {
        Order order = seed();
        CancellationAttempt attempt = writer.start(owner, order.getId(), UUID.randomUUID());
        jdbc.update("UPDATE order_schema.p_orders SET status='CANCELED' WHERE order_id=?", order.getId());

        assertThat(writer.resolveCompletion(attempt)).isEqualTo(CancellationCompletionOutcome.UNKNOWN);
    }

    @Test
    @DisplayName("외부 트랜잭션 안에서 취소를 호출하면 실행권·배송 호출 전에 차단한다")
    void rejectsOuterTransaction() {
        Order order = seed();

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> service.cancel(owner, order.getId())))
                .isInstanceOf(IllegalTransactionStateException.class);
        verifyNoInteractions(delivery);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_schema.p_order_cancellations WHERE order_id=?", Integer.class, order.getId())).isZero();
    }

    private void assertLocalFailure(Order order) {
        assertThatThrownBy(() -> service.cancel(owner, order.getId())).extracting("errorCode").isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR);
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.ORDERED);
    }

    private void assertRolledBackBeforeCompensation(Order order) {
        willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.ORDERED);
            assertThat(recordStatus(order.getId())).isEqualTo("PROCESSING");
            return null;
        }).given(delivery).revertCancellation(order.getDeliveryId());
    }

    private CancellationAttempt attempt(Order order) {
        return new CancellationAttempt(jdbc.queryForObject("SELECT order_cancellation_id FROM order_schema.p_order_cancellations WHERE order_id=?",
                UUID.class, order.getId()), order.getId(), order.getDeliveryId());
    }

    private String recordStatus(UUID orderId) {
        return jdbc.queryForObject("SELECT status FROM order_schema.p_order_cancellations WHERE order_id=?", String.class, orderId);
    }

    private Order seed() {
        Order order = orders.saveAndFlush(Order.create(UUID.randomUUID(), owner.userId(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "주문 당시 상품", 12_000L, 50, UUID.randomUUID(), "오전 도착 희망"));
        fixtureIds.add(order.getId());
        return order;
    }
}
