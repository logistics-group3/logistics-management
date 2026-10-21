package com.sparta.logistics.order.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.common.exception.SimpleErrorCode;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.order.application.CompanyReader;
import com.sparta.logistics.order.application.DeliveryGateway;
import com.sparta.logistics.order.application.OrderCreationQueryService;
import com.sparta.logistics.order.application.OrderNotificationSender;
import com.sparta.logistics.order.application.OrderService;
import com.sparta.logistics.order.application.dto.CompanyResult;
import com.sparta.logistics.order.application.dto.CreateDeliveryCommand;
import com.sparta.logistics.order.application.dto.CreateOrderCommand;
import com.sparta.logistics.order.application.dto.OrderCreationOutcome;
import com.sparta.logistics.order.application.dto.OrderCreationProgress;
import com.sparta.logistics.order.application.dto.ProductResult;
import com.sparta.logistics.order.domain.OrderCreationStatus;
import com.sparta.logistics.order.domain.OrderErrorCode;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 실제 스레드풀로 비동기 접수를 검증한다(이슈 #16). 접수는 처리 단계(I-09 → 저장 → 보상)가 끝나기 전에 반환하고,
 * 결과는 진행 조회로 확인하며, 실행기가 가득 차면 원격 호출 없이 503으로 끝난다.
 * 스레드 1개·대기열 1개로 줄여 포화를 만든다. 외부 통신은 Mock이고 DB는 실제 PostgreSQL이다.
 */
@TestPropertySource(properties = {
        "spring.flyway.schemas=order_schema",
        "spring.jpa.properties.hibernate.default_schema=order_schema",
        "order.creation.executor.threads=1",
        "order.creation.executor.queue-capacity=1"
})
class OrderCreationAsyncIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private OrderService service;
    @Autowired
    private OrderCreationQueryService queryService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoBean
    private OrderNotificationSender notificationSender;
    @MockitoBean
    private CompanyReader companyReader;
    @MockitoBean
    private DeliveryGateway deliveryGateway;

    private final UserContext user = new UserContext(UUID.randomUUID(), "junhyun", Role.USER, false);
    private final CompanyResult supplier = new CompanyResult(UUID.randomUUID(), "공급업체", UUID.randomUUID(),
            "서울 집하지", UUID.randomUUID());
    private final CompanyResult receiver = new CompanyResult(UUID.randomUUID(), "수령업체", UUID.randomUUID(),
            "부산 배송지", user.userId());
    private final ProductResult product = new ProductResult(UUID.randomUUID(), supplier.companyId(), "상품", 12_000L);
    private final CreateOrderCommand command = new CreateOrderCommand(
            supplier.companyId(), receiver.companyId(), product.productId(), 50, null);
    private final CountDownLatch release = new CountDownLatch(1);

    @AfterEach
    void releaseBlockedWorkers() {
        release.countDown();
    }

    @Test
    @DisplayName("접수는 배송 생성이 끝나기 전에 반환하고, 처리 중에는 PROCESSING이다가 끝나면 COMPLETED와 주문을 보여 준다")
    void acceptReturnsBeforeProcessingFinishes() throws Exception {
        stubCompanies();
        CountDownLatch entered = new CountDownLatch(1);
        given(deliveryGateway.create(any())).willAnswer(invocation -> {
            entered.countDown();
            release.await(10, TimeUnit.SECONDS);
            return UUID.randomUUID();
        });

        OrderCreationOutcome accepted = service.accept(user, "async-key", command);

        assertThat(accepted.status()).isEqualTo(OrderCreationStatus.PROCESSING);
        assertThat(accepted.order()).isNull();
        assertThat(entered.await(10, TimeUnit.SECONDS)).as("처리 단계가 배송 생성 호출에서 멈춘다").isTrue();
        assertThat(progress(accepted.orderId()).status()).isEqualTo(OrderCreationStatus.PROCESSING);
        assertThat(orderRows(accepted.orderId())).isZero();

        // 처리 중에 같은 키·본문으로 다시 보내면 같은 orderId로 접수된 상태를 돌려주고 배송 생성을 다시 하지 않는다.
        OrderCreationOutcome replayWhileProcessing = service.accept(user, "async-key", command);
        assertThat(replayWhileProcessing.orderId()).isEqualTo(accepted.orderId());
        assertThat(replayWhileProcessing.order()).isNull();

        release.countDown();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(
                () -> assertThat(progress(accepted.orderId()).status()).isEqualTo(OrderCreationStatus.COMPLETED));
        OrderCreationProgress completed = progress(accepted.orderId());
        assertThat(completed.order().orderId()).isEqualTo(accepted.orderId());
        assertThat(completed.order().ordererId()).isEqualTo(user.userId());
        assertThat(completed.failureCode()).isNull();
        verify(deliveryGateway, org.mockito.Mockito.times(1)).create(any());

        // 완료 뒤 같은 키 재요청은 최초 응답을 돌려준다.
        OrderCreationOutcome replayAfterCompletion = service.accept(user, "async-key", command);
        assertThat(replayAfterCompletion.status()).isEqualTo(OrderCreationStatus.COMPLETED);
        assertThat(replayAfterCompletion.order().orderId()).isEqualTo(accepted.orderId());
    }

    @Test
    @DisplayName("배송 생성이 하위 서비스의 4xx로 거절되면 ABORTED이고 진행 조회가 원인 코드와 안내를 보여 준다")
    void downstreamRejectionIsVisibleInProgress() {
        stubCompanies();
        given(deliveryGateway.create(any())).willThrow(new BusinessException(
                new SimpleErrorCode(HttpStatus.NOT_FOUND, "DELIVERY_MANAGER_NOT_FOUND", "배정할 배송담당자가 없습니다.")));

        OrderCreationOutcome accepted = service.accept(user, "rejected-key", command);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(
                () -> assertThat(progress(accepted.orderId()).status()).isEqualTo(OrderCreationStatus.ABORTED));
        OrderCreationProgress progress = progress(accepted.orderId());
        assertThat(progress.failureCode()).isEqualTo("DELIVERY_MANAGER_NOT_FOUND");
        assertThat(progress.failureMessage()).isEqualTo("배정할 배송담당자가 없습니다.");
        assertThat(progress.order()).isNull();
        verify(deliveryGateway, never()).closeCreation(any());
        // 같은 키로 다시 확인하면 거절이 아니라 기존 실패 종료(409)다.
        assertThatThrownBy(() -> service.accept(user, "rejected-key", command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(OrderErrorCode.ORDER_CREATION_ABORTED);
    }

    @Test
    @DisplayName("실행기가 가득 차면 원격 호출 없이 시도를 ABORTED로 닫고 503을 돌려준다")
    void saturatedExecutorRejectsWithoutRemoteCalls() throws Exception {
        stubCompanies();
        CountDownLatch entered = new CountDownLatch(1);
        List<UUID> deliveryOrderIds = new CopyOnWriteArrayList<>();
        given(deliveryGateway.create(any())).willAnswer(invocation -> {
            deliveryOrderIds.add(((CreateDeliveryCommand) invocation.getArgument(0)).orderId());
            entered.countDown();
            release.await(10, TimeUnit.SECONDS);
            return UUID.randomUUID();
        });

        OrderCreationOutcome running = service.accept(user, "running-key", command);
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        OrderCreationOutcome queued = service.accept(user, "queued-key", command);

        assertThatThrownBy(() -> service.accept(user, "rejected-key", command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
        UUID rejectedOrderId = jdbcTemplate.queryForObject(
                "SELECT order_id FROM order_schema.p_order_creations WHERE idempotency_key = 'rejected-key'", UUID.class);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM order_schema.p_order_creations WHERE order_id = ?",
                String.class, rejectedOrderId)).isEqualTo("ABORTED");
        assertThat(progress(rejectedOrderId).failureCode()).isEqualTo("SERVICE_UNAVAILABLE");

        release.countDown();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(progress(running.orderId()).status()).isEqualTo(OrderCreationStatus.COMPLETED);
            assertThat(progress(queued.orderId()).status()).isEqualTo(OrderCreationStatus.COMPLETED);
        });
        assertThat(deliveryOrderIds).containsExactlyInAnyOrder(running.orderId(), queued.orderId())
                .doesNotContain(rejectedOrderId);
    }

    @Test
    @DisplayName("진행 조회는 주문자 본인과 MASTER만 볼 수 있고 없는 기록은 404다")
    void progressAuthorization() {
        stubCompanies();
        given(deliveryGateway.create(any())).willReturn(UUID.randomUUID());
        OrderCreationOutcome accepted = service.accept(user, "auth-key", command);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(
                () -> assertThat(progress(accepted.orderId()).status()).isEqualTo(OrderCreationStatus.COMPLETED));

        UserContext other = new UserContext(UUID.randomUUID(), "other", Role.USER, false);
        UserContext master = new UserContext(UUID.randomUUID(), "master", Role.MASTER, false);
        assertThatThrownBy(() -> queryService.getProgress(other, accepted.orderId()))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(CommonErrorCode.FORBIDDEN);
        assertThat(queryService.getProgress(master, accepted.orderId()).status()).isEqualTo(OrderCreationStatus.COMPLETED);
        assertThatThrownBy(() -> queryService.getProgress(user, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(OrderErrorCode.ORDER_CREATION_NOT_FOUND);
    }

    private OrderCreationProgress progress(UUID orderId) {
        return queryService.getProgress(user, orderId);
    }

    private int orderRows(UUID orderId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM order_schema.p_orders WHERE order_id = ?", Integer.class, orderId);
    }

    private void stubCompanies() {
        given(companyReader.getCompany(supplier.companyId())).willReturn(supplier);
        given(companyReader.getCompany(receiver.companyId())).willReturn(receiver);
        given(companyReader.getProduct(product.productId())).willReturn(product);
    }
}
