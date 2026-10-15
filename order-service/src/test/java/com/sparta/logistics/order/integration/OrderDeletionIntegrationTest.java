package com.sparta.logistics.order.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.order.application.CompanyReader;
import com.sparta.logistics.order.application.DeliveryGateway;
import com.sparta.logistics.order.application.DeliveryReader;
import com.sparta.logistics.order.application.OrderCancellationService;
import com.sparta.logistics.order.application.OrderDeletionService;
import com.sparta.logistics.order.application.OrderDeletionWriter;
import com.sparta.logistics.order.application.OrderNotificationSender;
import com.sparta.logistics.order.application.dto.OrderDeletionOutcome;
import com.sparta.logistics.order.domain.CancellationFailureReason;
import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.domain.OrderCancellation;
import com.sparta.logistics.order.domain.OrderCancellationRepository;
import com.sparta.logistics.order.domain.OrderDeletion;
import com.sparta.logistics.order.domain.OrderDeletionRepository;
import com.sparta.logistics.order.domain.OrderErrorCode;
import com.sparta.logistics.order.domain.OrderRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@TestPropertySource(properties = {
        "spring.flyway.schemas=order_schema", "spring.jpa.properties.hibernate.default_schema=order_schema",
        "spring.jpa.open-in-view=false", "slack.webhook-url="
})
@Import(OrderDeletionIntegrationTest.FailureConfig.class)
class OrderDeletionIntegrationTest extends PostgresIntegrationTest {
    @Autowired private OrderDeletionService service;
    @Autowired private OrderDeletionWriter writer;
    @Autowired private OrderCancellationService cancellationService;
    @Autowired private OrderRepository orders;
    @Autowired private OrderDeletionRepository deletions;
    @Autowired private OrderCancellationRepository cancellations;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;
    @Autowired private DataSource dataSource;
    @Autowired private FailureAfterCommit failure;
    @MockitoBean private DeliveryGateway delivery;
    @MockitoBean private DeliveryReader reader;
    @MockitoBean private CompanyReader company;
    @MockitoBean private OrderNotificationSender notification;
    private final UserContext master = new UserContext(UUID.randomUUID(), "delete-master", Role.MASTER, false);
    private final UserContext owner = new UserContext(UUID.randomUUID(), "owner", Role.USER, false);
    private final List<UUID> ids = new ArrayList<>();

    @BeforeEach
    void resetFailure() { failure.afterCommit.set(false); failure.verification.set(false); }

    @AfterEach
    void cleanUp() {
        failure.afterCommit.set(false); failure.verification.set(false);
        try { verifyNoInteractions(company, notification); }
        finally {
            jdbc.execute("ALTER TABLE order_schema.p_orders DROP CONSTRAINT IF EXISTS ck_test_delete_order");
            jdbc.execute("ALTER TABLE order_schema.p_order_deletions DROP CONSTRAINT IF EXISTS ck_test_delete_completion");
            jdbc.execute("DROP TRIGGER IF EXISTS test_delete_commit_failure ON order_schema.p_orders");
            jdbc.execute("DROP FUNCTION IF EXISTS order_schema.fail_test_delete_commit()");
            ids.forEach(id -> {
                jdbc.update("DELETE FROM order_schema.p_order_deletions WHERE order_id=?", id);
                jdbc.update("DELETE FROM order_schema.p_order_cancellations WHERE order_id=?", id);
                jdbc.update("DELETE FROM order_schema.p_orders WHERE order_id=?", id);
            });
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("취소·배송 완료 주문 삭제는 허가 근거를 먼저 커밋하고 주문·완료 기록을 함께 저장한다")
    void deletesEligibleOrder(boolean canceled) {
        Order order = seed(canceled);
        if (!canceled) given(reader.isDelivered(order.getId(), order.getDeliveryId())).willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return true;
        });
        willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            try (var connection = dataSource.getConnection(); var query = connection.prepareStatement(
                    "SELECT o.deleted_at, d.status FROM order_schema.p_orders o JOIN order_schema.p_order_deletions d ON d.order_id=o.order_id WHERE o.order_id=?")) {
                query.setObject(1, order.getId());
                try (var row = query.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getTimestamp(1)).isNull();
                    assertThat(row.getString(2)).isEqualTo("PROCESSING");
                }
            }
            return null;
        }).given(delivery).deleteByOrderId(order.getId());
        service.delete(master, order.getId());
        assertDeleted(order);
        assertThat(text(order, "eligibility")).isEqualTo(canceled ? "ORDER_CANCELED" : "DELIVERY_DELIVERED");
        assertThat(jdbc.queryForObject("SELECT deleted_by FROM order_schema.p_orders WHERE order_id=?", String.class, order.getId()))
                .isEqualTo(master.username());
        assertThat(writer.resolve(order.getId())).isEqualTo(OrderDeletionOutcome.COMMITTED);
        if (canceled) verifyNoInteractions(reader);
    }

    @Test
    @DisplayName("외부 트랜잭션 안에서 전체 삭제 흐름을 실행하지 못한다")
    void refusesOuterTransaction() {
        Order order = seed(true);
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> service.delete(master, order.getId())))
                .isInstanceOf(IllegalTransactionStateException.class);
        verifyNoInteractions(reader, delivery);
    }

    @Test
    @DisplayName("배송이 진행 중이면 삭제 기록·원격 삭제를 만들지 않는다")
    void refusesActiveDelivery() {
        Order order = seed(false);
        assertThatThrownBy(() -> service.delete(master, order.getId())).extracting("errorCode").isEqualTo(OrderErrorCode.ORDER_NOT_DELETABLE);
        assertThat(deletions.findByOrderIdIncludingDeleted(order.getId())).isEmpty();
        verifyNoInteractions(delivery);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("진행 중·복구 대기 취소가 있으면 배송 조회 전에 삭제를 막는다")
    void refusesUnresolvedCancellation(boolean recovery) {
        Order order = seed(false);
        transactions.executeWithoutResult(status -> {
            OrderCancellation record = OrderCancellation.start(UUID.randomUUID(), order.getId(), order.getDeliveryId());
            if (recovery) record.requireRecovery(CancellationFailureReason.DELIVERY_RESULT_UNKNOWN);
            cancellations.saveAndFlush(record);
        });
        assertThatThrownBy(() -> service.delete(master, order.getId())).extracting("errorCode").isEqualTo(OrderErrorCode.ORDER_NOT_DELETABLE);
        verifyNoInteractions(reader, delivery);
    }

    @Test
    @DisplayName("삭제 기록이 남은 주문은 새 취소와 원복 흐름에 진입하지 않는다")
    void blocksCancellationAfterDeletionStarts() {
        Order order = seed(false);
        writer.start(writer.inspect(order.getId()), true);
        assertThatThrownBy(() -> cancellationService.cancel(owner, order.getId()))
                .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_STATUS_TRANSITION);
        verifyNoInteractions(reader, delivery);
    }

    @Test
    @DisplayName("원격 삭제 실패는 로컬 삭제를 롤백하고 같은 주문 재요청은 저장된 허가로 이어간다")
    void retriesRemoteFailure() {
        Order order = seed(false);
        given(reader.isDelivered(order.getId(), order.getDeliveryId())).willReturn(true);
        AtomicBoolean first = new AtomicBoolean(true);
        willAnswer(invocation -> {
            if (first.getAndSet(false)) throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
            return null;
        }).given(delivery).deleteByOrderId(order.getId());
        assertThatThrownBy(() -> service.delete(master, order.getId())).extracting("errorCode").isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
        assertThat(orders.findById(order.getId())).isPresent();
        assertThat(text(order, "status")).isEqualTo("RECOVERY_REQUIRED");
        service.delete(master, order.getId());
        assertDeleted(order);
        verify(reader, times(1)).isDelivered(any(), any());
        verify(delivery, times(2)).deleteByOrderId(order.getId());
    }

    @Test
    @DisplayName("주문 flush 제약 오류는 I-13 전에 발견하고 삭제 기록을 보존한다")
    void detectsLocalFailureBeforeRemote() {
        Order order = seed(true);
        jdbc.execute("ALTER TABLE order_schema.p_orders ADD CONSTRAINT ck_test_delete_order CHECK (order_id <> '" + order.getId() + "'::uuid OR deleted_at IS NULL)");
        assertThatThrownBy(() -> service.delete(master, order.getId())).extracting("errorCode").isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR);
        assertThat(orders.findById(order.getId())).isPresent();
        assertThat(text(order, "failure_reason")).isEqualTo("ORDER_WRITE_FAILED");
        verifyNoInteractions(delivery);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("완료 기록·커밋 실패로 배송만 삭제돼도 재요청은 I-10 없이 I-13과 주문 삭제를 완료한다")
    void resumesAfterRemoteDeleteAndLocalRollback(boolean commitFailure) {
        Order order = seed(false);
        given(reader.isDelivered(order.getId(), order.getDeliveryId())).willReturn(true);
        if (commitFailure) {
            jdbc.execute("CREATE FUNCTION order_schema.fail_test_delete_commit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.order_id='" + order.getId() + "'::uuid AND NEW.deleted_at IS NOT NULL THEN RAISE EXCEPTION 'injected delete commit failure'; END IF; RETURN NEW; END; $$");
            jdbc.execute("CREATE CONSTRAINT TRIGGER test_delete_commit_failure AFTER UPDATE ON order_schema.p_orders DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION order_schema.fail_test_delete_commit()");
        } else {
            jdbc.execute("ALTER TABLE order_schema.p_order_deletions ADD CONSTRAINT ck_test_delete_completion CHECK (order_id <> '" + order.getId() + "'::uuid OR status <> 'COMPLETED')");
        }
        assertThatThrownBy(() -> service.delete(master, order.getId())).extracting("errorCode").isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR);
        assertThat(orders.findById(order.getId())).isPresent();
        assertThat(text(order, "status")).isEqualTo("RECOVERY_REQUIRED");
        verify(delivery).deleteByOrderId(order.getId());
        jdbc.execute("ALTER TABLE order_schema.p_order_deletions DROP CONSTRAINT IF EXISTS ck_test_delete_completion");
        jdbc.execute("DROP TRIGGER IF EXISTS test_delete_commit_failure ON order_schema.p_orders");
        service.delete(master, order.getId());
        assertDeleted(order);
        verify(reader, times(1)).isDelivered(any(), any());
        verify(delivery, times(2)).deleteByOrderId(order.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("실제 삭제 커밋 뒤 반환 예외가 나도 완료 기록을 복구 대기로 덮지 않는다")
    void confirmsActualCommit(boolean verificationFailure) {
        Order order = seed(true);
        failure.afterCommit.set(true);
        failure.verification.set(verificationFailure);
        if (verificationFailure) {
            assertThatThrownBy(() -> service.delete(master, order.getId())).extracting("errorCode").isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR);
        } else service.delete(master, order.getId());
        assertDeleted(order);
        verify(delivery, times(1)).deleteByOrderId(order.getId());
    }

    @Test
    @DisplayName("동일 주문의 동시 삭제는 실제 PostgreSQL 잠금으로 대기하며 I-13을 중복 호출하지 않는다")
    void serializesDuplicateDeletes() throws Exception {
        Order order = seed(true);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger blocker = new AtomicInteger();
        willAnswer(invocation -> {
            blocker.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
            entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return null;
        }).given(delivery).deleteByOrderId(order.getId());
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> service.delete(master, order.getId()));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> {
                try { service.delete(master, order.getId()); return "deleted"; }
                catch (BusinessException error) { return error.getErrorCode().code(); }
            });
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))", Integer.class, blocker.get())).isPositive());
            release.countDown(); first.get(5, TimeUnit.SECONDS);
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("ORDER_NOT_FOUND");
            assertDeleted(order);
            verify(delivery, times(1)).deleteByOrderId(order.getId());
        } finally { release.countDown(); executor.shutdown(); assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test
    @DisplayName("진행 기록 논리 삭제는 재시작을 허용하지 않으며 삭제 이력도 UNIQUE에 포함된다")
    void preservesDeletedRecord() {
        Order order = seed(true);
        writer.start(writer.inspect(order.getId()), false);
        transactions.executeWithoutResult(status -> deletions.findByOrderIdIncludingDeleted(order.getId()).orElseThrow().softDelete("fixture"));
        assertThatThrownBy(() -> service.delete(master, order.getId())).extracting("errorCode").isEqualTo(OrderErrorCode.ORDER_NOT_DELETABLE);
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> deletions.saveAndFlush(OrderDeletion.start(UUID.randomUUID(), order, false))))
                .isInstanceOf(DataIntegrityViolationException.class);
        verifyNoInteractions(reader, delivery);
    }

    @ParameterizedTest
    @ValueSource(strings = {"status='BAD'", "eligibility='BAD'", "status='RECOVERY_REQUIRED'", "failure_reason='BAD'", "failure_reason='ORDER_WRITE_FAILED'", "order_id=gen_random_uuid()"})
    @DisplayName("DB CHECK와 같은 스키마 FK가 삭제 기록의 상태·근거·사유·주문 참조를 보호한다")
    void validatesDatabaseContract(String assignment) {
        Order order = seed(true);
        writer.start(writer.inspect(order.getId()), false);
        assertThatThrownBy(() -> jdbc.update("UPDATE order_schema.p_order_deletions SET " + assignment + " WHERE order_id=?", order.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Order seed(boolean canceled) {
        Order order = Order.create(UUID.randomUUID(), owner.userId(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "상품", 12000L, 2, UUID.randomUUID(), "기존 요청사항");
        if (canceled) order.cancel();
        ids.add(order.getId());
        return transactions.execute(status -> orders.saveAndFlush(order));
    }
    private String text(Order order, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM order_schema.p_order_deletions WHERE order_id=?", String.class, order.getId());
    }
    private void assertDeleted(Order order) {
        assertThat(orders.findById(order.getId())).isEmpty();
        assertThat(text(order, "status")).isEqualTo("COMPLETED");
        assertThat(text(order, "failure_reason")).isNull();
    }

    @TestConfiguration
    @EnableAspectJAutoProxy
    static class FailureConfig { @Bean FailureAfterCommit failureAfterCommit() { return new FailureAfterCommit(); } }

    @Aspect
    @org.springframework.core.annotation.Order(Integer.MIN_VALUE)
    static class FailureAfterCommit {
        final AtomicBoolean afterCommit = new AtomicBoolean(), verification = new AtomicBoolean();
        @Around("execution(* com.sparta.logistics.order.application.OrderDeletionWriter.delete(..))")
        Object failAfterCommit(ProceedingJoinPoint call) throws Throwable {
            Object result = call.proceed();
            if (afterCommit.getAndSet(false)) throw new DataAccessResourceFailureException("injected after committed delete");
            return result;
        }
        @Around("execution(* com.sparta.logistics.order.application.OrderDeletionWriter.resolve(..))")
        Object failVerification(ProceedingJoinPoint call) throws Throwable {
            if (verification.get()) throw new DataAccessResourceFailureException("injected verification failure");
            return call.proceed();
        }
    }
}
