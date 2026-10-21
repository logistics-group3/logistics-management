package com.sparta.logistics.order.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.auth.UserHeaders;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.order.application.CompanyReader;
import com.sparta.logistics.order.application.DeliveryGateway;
import com.sparta.logistics.order.application.OrderCreationRecoveryService;
import com.sparta.logistics.order.application.OrderCreationRecoveryWriter;
import com.sparta.logistics.order.application.OrderNotificationSender;
import com.sparta.logistics.order.application.OrderWriter;
import com.sparta.logistics.order.application.dto.CreateOrderCommand;
import com.sparta.logistics.order.application.dto.DeliveryCreationState;
import com.sparta.logistics.order.application.dto.OrderCreationAttempt;
import com.sparta.logistics.order.application.dto.RecoverOrderCreationCommand;
import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.domain.OrderCreationRecovery;
import com.sparta.logistics.order.domain.OrderCreationStatus;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@TestPropertySource(properties = {"spring.flyway.schemas=order_schema", "spring.jpa.properties.hibernate.default_schema=order_schema"})
class OrderCreationRecoveryIntegrationTest extends PostgresIntegrationTest {
    @MockitoBean private CompanyReader companies;
    @MockitoBean private DeliveryGateway delivery;
    @MockitoBean private OrderNotificationSender notification;
    @Autowired private OrderWriter writer;
    @Autowired private OrderCreationRecoveryWriter recoveryWriter;
    @Autowired private OrderCreationRecoveryService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;

    private final UserContext master = new UserContext(UUID.randomUUID(), "operator", Role.MASTER, false);
    private final OrderCreationAttempt attempt = new OrderCreationAttempt(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    private final UUID requestId = UUID.randomUUID();

    @AfterEach
    void removeFault() {
        jdbc.execute("ALTER TABLE order_schema.p_order_creations DROP CONSTRAINT IF EXISTS ck_test_recovery_abort");
    }

    @Test
    @DisplayName("NONE은 조건부 종료와 CLOSED 재확인 뒤에만 ABORTED로 닫고 이력을 남긴다")
    void recoversNoneAndReplaysWithoutSideEffects() {
        start();
        given(delivery.getCreationState(attempt.orderId())).willReturn(state("NONE", null, null), state("CLOSED", null, null));
        var result = service.recover(master, attempt.orderId(), command());
        assertThat(result.status()).isEqualTo(OrderCreationRecovery.Status.COMPLETED);
        assertThat(result.reason()).isEqualTo(command().reason());
        assertThat(result.expectedStatus()).isEqualTo(OrderCreationStatus.PROCESSING);
        assertThat(result.requestedBy()).isEqualTo(master.userId());
        assertThat(result.events()).extracting(event -> event.step()).containsExactly("CLAIMED", "OBSERVED", "CLOSE_REQUESTED", "VERIFIED", "COMPLETED");
        assertThat(creationStatus()).isEqualTo("ABORTED");
        assertThat(jdbc.queryForObject("SELECT failure_reason FROM order_schema.p_order_creations WHERE order_id=?", String.class, attempt.orderId())).isNull();
        assertThat(service.recover(master, attempt.orderId(), command()).executionId()).isEqualTo(result.executionId());
        verify(delivery, times(2)).getCreationState(attempt.orderId());
        verify(delivery).closeCreation(attempt.orderId(), null);
    }

    @Test
    @DisplayName("배송이 있으면 관찰한 ID를 조건부 종료에 전달한다")
    void recoversWaitingDelivery() {
        start();
        UUID deliveryId = UUID.randomUUID();
        given(delivery.getCreationState(attempt.orderId())).willReturn(state("ACTIVE", deliveryId, "PICKUP_WAITING"), state("CLOSED", null, null));
        assertThat(service.recover(master, attempt.orderId(), command()).status()).isEqualTo(OrderCreationRecovery.Status.COMPLETED);
        verify(delivery).closeCreation(attempt.orderId(), deliveryId);
    }

    @Test
    @DisplayName("CLOSED는 다시 삭제하지 않고 로컬 종료만 마친다")
    void finishesAlreadyClosedAttempt() {
        start();
        given(delivery.getCreationState(attempt.orderId())).willReturn(state("CLOSED", null, null));
        service.recover(master, attempt.orderId(), command());
        assertThat(creationStatus()).isEqualTo("ABORTED");
        verify(delivery, never()).closeCreation(any(), any());
    }

    @Test
    @DisplayName("배송 진행 상태는 정리하지 않고 복구 대기로 보존한다")
    void preservesMovingDelivery() {
        start();
        given(delivery.getCreationState(attempt.orderId())).willReturn(state("ACTIVE", UUID.randomUUID(), "PICKING_UP"));
        assertThat(service.recover(master, attempt.orderId(), command()).status()).isEqualTo(OrderCreationRecovery.Status.DEFERRED);
        assertThat(creationStatus()).isEqualTo("RECOVERY_REQUIRED");
        verify(delivery, never()).closeCreation(any(), any());
    }

    @Test
    @DisplayName("조회 실패는 보류하며 같은 복구 ID로 확인이 가능해지면 재개한다")
    void retriesDeferredLookup() {
        start();
        given(delivery.getCreationState(attempt.orderId())).willThrow(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE));
        var deferred = service.recover(master, attempt.orderId(), command());
        assertThat(deferred.status()).isEqualTo(OrderCreationRecovery.Status.DEFERRED);
        willReturn(state("CLOSED", null, null)).given(delivery).getCreationState(attempt.orderId());
        var completed = service.recover(master, attempt.orderId(), command());
        assertThat(completed.status()).isEqualTo(OrderCreationRecovery.Status.COMPLETED);
        assertThat(completed.executionId()).isNotEqualTo(deferred.executionId());
    }

    @Test
    @DisplayName("조건부 종료 응답이 유실돼도 I-17 CLOSED로 실제 결과를 확인하면 종료한다")
    void resolvesLostCloseResponse() {
        start();
        given(delivery.getCreationState(attempt.orderId())).willReturn(state("NONE", null, null), state("CLOSED", null, null));
        willThrow(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE)).given(delivery).closeCreation(attempt.orderId(), null);
        var result = service.recover(master, attempt.orderId(), command());
        assertThat(result.status()).isEqualTo(OrderCreationRecovery.Status.COMPLETED);
        assertThat(result.events()).extracting(event -> event.step()).contains("CLOSE_UNCONFIRMED", "VERIFIED", "COMPLETED");
    }

    @Test
    @DisplayName("같은 복구 ID의 다른 사유와 같은 시도의 다른 복구 ID는 원격 변경 없이 거절한다")
    void rejectsConflictingRecoveryIdentity() {
        start();
        recoveryWriter.claim(attempt.orderId(), master.userId(), command());
        var changed = new RecoverOrderCreationCommand(requestId, attempt.attemptId(), OrderCreationStatus.PROCESSING, "다른 사유", true, null);
        var another = new RecoverOrderCreationCommand(UUID.randomUUID(), attempt.attemptId(), OrderCreationStatus.PROCESSING, "프로세스 중단 확인", true, null);
        assertThatThrownBy(() -> service.recover(master, attempt.orderId(), changed)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.recover(master, attempt.orderId(), another)).isInstanceOf(BusinessException.class);
        verify(delivery, never()).getCreationState(any());
    }

    @Test
    @DisplayName("같은 복구의 동시 요청은 원격 정리를 중복 실행하지 않는다")
    void duplicateWhileRunningOnlyObserves() throws Exception {
        start();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        given(delivery.getCreationState(attempt.orderId())).willAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return state("CLOSED", null, null);
        });
        var executor = Executors.newSingleThreadExecutor();
        try {
            var first = executor.submit(() -> service.recover(master, attempt.orderId(), command()));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(service.recover(master, attempt.orderId(), command()).status()).isEqualTo(OrderCreationRecovery.Status.RUNNING);
            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).status()).isEqualTo(OrderCreationRecovery.Status.COMPLETED);
            verify(delivery, times(2)).getCreationState(attempt.orderId());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("복구 인계 후 늦은 원래 저장과 오래된 복구 실행의 갱신은 거절한다")
    void fencesNormalWriterAndOldRecoveryExecution() {
        start();
        var claim = recoveryWriter.claim(attempt.orderId(), master.userId(), command());
        Order order = order();
        assertThatThrownBy(() -> writer.save(order, "공급", "수령")).isInstanceOf(BusinessException.class);
        var resumed = recoveryWriter.claim(attempt.orderId(), master.userId(),
                new RecoverOrderCreationCommand(requestId, attempt.attemptId(), OrderCreationStatus.PROCESSING, "프로세스 중단 확인", true, claim.executionId()));
        assertThat(resumed.executionId()).isNotEqualTo(claim.executionId());
        assertThatThrownBy(() -> recoveryWriter.finish(attempt.orderId(), requestId, claim.executionId())).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_schema.p_orders WHERE order_id=?", Integer.class, attempt.orderId())).isZero();
    }

    @Test
    @DisplayName("배송 정리 후 로컬 저장이 실패해도 완료로 표시하지 않고 같은 요청으로 재개한다")
    void resumesAfterLocalFinalizationFailure() {
        start();
        given(delivery.getCreationState(attempt.orderId())).willReturn(state("NONE", null, null), state("CLOSED", null, null));
        jdbc.execute("ALTER TABLE order_schema.p_order_creations ADD CONSTRAINT ck_test_recovery_abort CHECK (order_id <> '" + attempt.orderId() + "'::uuid OR status <> 'ABORTED')");
        assertThat(service.recover(master, attempt.orderId(), command()).status()).isEqualTo(OrderCreationRecovery.Status.DEFERRED);
        assertThat(creationStatus()).isEqualTo("RECOVERY_REQUIRED");
        removeFault();
        given(delivery.getCreationState(attempt.orderId())).willReturn(state("CLOSED", null, null));
        assertThat(service.recover(master, attempt.orderId(), command()).status()).isEqualTo(OrderCreationRecovery.Status.COMPLETED);
        verify(delivery, times(1)).closeCreation(attempt.orderId(), null);
    }

    @Test
    @DisplayName("기존 주문이나 논리 삭제 이력이 있으면 원격 변경 없이 거절한다")
    void rejectsExistingOrderAndChangedRequest() {
        start();
        writer.save(order(), "공급", "수령");
        assertThatThrownBy(() -> service.recover(master, attempt.orderId(), command())).isInstanceOf(BusinessException.class);
        jdbc.update("UPDATE order_schema.p_order_creations SET status='PROCESSING', response_snapshot=NULL WHERE order_id=?", attempt.orderId());
        jdbc.update("UPDATE order_schema.p_orders SET deleted_at=now() WHERE order_id=?", attempt.orderId());
        assertThatThrownBy(() -> service.recover(master, attempt.orderId(), command())).isInstanceOf(BusinessException.class);
        verify(delivery, never()).getCreationState(any());
    }

    @Test
    @DisplayName("MASTER만 목록·이력을 읽고 중단 확인이 없는 복구 요청은 거절한다")
    void enforcesRoleAndStoppedConfirmation() throws Exception {
        mvc.perform(get("/api/v1/orders/creation-recoveries/candidates")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/orders/creation-recoveries/candidates").header(UserHeaders.USER_ID, master.userId())
                .header(UserHeaders.USERNAME, "user").header(UserHeaders.ROLE, "USER")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/orders/creation-recoveries/candidates").header(UserHeaders.USER_ID, master.userId())
                .header(UserHeaders.USERNAME, "operator").header(UserHeaders.ROLE, "MASTER")).andExpect(status().isOk());
        start();
        var unconfirmed = new RecoverOrderCreationCommand(requestId, attempt.attemptId(), OrderCreationStatus.PROCESSING, "중단 미확인", false, null);
        mvc.perform(post("/api/v1/orders/{orderId}/creation-recoveries", attempt.orderId())
                .header(UserHeaders.USER_ID, master.userId()).header(UserHeaders.USERNAME, "operator").header(UserHeaders.ROLE, "MASTER")
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(unconfirmed))).andExpect(status().isBadRequest());
        assertThat(creationStatus()).isEqualTo("PROCESSING");
    }

    private void start() {
        writer.start(attempt, new CreateOrderCommand(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 2, null).identity(UUID.randomUUID().toString()));
    }

    private RecoverOrderCreationCommand command() {
        return new RecoverOrderCreationCommand(requestId, attempt.attemptId(), OrderCreationStatus.PROCESSING, "프로세스 중단 확인", true, null);
    }

    private DeliveryCreationState state(String state, UUID deliveryId, String status) {
        return new DeliveryCreationState(attempt.orderId(), state, deliveryId, status);
    }

    private String creationStatus() {
        return jdbc.queryForObject("SELECT status FROM order_schema.p_order_creations WHERE order_id=?", String.class, attempt.orderId());
    }

    private Order order() {
        return Order.create(attempt.orderId(), attempt.ordererId(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "테스트 상품", 1000L, 2, UUID.randomUUID(), null);
    }
}
