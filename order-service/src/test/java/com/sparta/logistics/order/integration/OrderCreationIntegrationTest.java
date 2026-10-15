package com.sparta.logistics.order.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.order.application.CompanyReader;
import com.sparta.logistics.order.application.DeliveryGateway;
import com.sparta.logistics.order.application.OrderService;
import com.sparta.logistics.order.application.OrderNotificationSender;
import com.sparta.logistics.order.application.event.OrderCreatedEvent;
import com.sparta.logistics.order.application.dto.CompanyResult;
import com.sparta.logistics.order.application.dto.CreateDeliveryCommand;
import com.sparta.logistics.order.application.dto.CreateOrderCommand;
import com.sparta.logistics.order.application.dto.OrderResult;
import com.sparta.logistics.order.application.dto.ProductResult;
import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.domain.OrderRepository;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 외부 통신만 Mock으로 대체하고 OrderWriter의 실제 트랜잭션 프록시와 PostgreSQL을 검증한다. */
@TestPropertySource(properties = {
        "spring.flyway.schemas=order_schema",
        "spring.jpa.properties.hibernate.default_schema=order_schema"
})
class OrderCreationIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private OrderService service;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
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
    private final UUID deliveryId = UUID.randomUUID();
    private final AtomicReference<UUID> createdOrderId = new AtomicReference<>();

    @Test
    @DisplayName("외부 호출은 트랜잭션 밖에서 하고 주문은 실제 커밋과 Audit 반영 후 반환한다")
    void commitsOrderBeforeReturning() {
        stubExternalServices();

        OrderResult result = service.create(user, command(null));

        assertThat(result.orderId()).isEqualTo(createdOrderId.get());
        assertThat(result.ordererId()).isEqualTo(user.userId());
        assertThat(result.totalPrice()).isEqualTo(600_000L);
        assertThat(result.deliveryId()).isEqualTo(deliveryId);
        assertThat(result.createdAt()).isNotNull();
        assertThat(result.updatedAt()).isNotNull();
        assertThat(orderRepository.findById(result.orderId())).isPresent();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT created_by FROM order_schema.p_orders WHERE order_id = ?", String.class, result.orderId()))
                .isEqualTo("SYSTEM");
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        verify(notificationSender).send(new OrderCreatedEvent(result.orderId(), supplier.name(), receiver.name(),
                product.name(), 50, null));
    }

    @Test
    @DisplayName("flush에서 DB 제약이 실패하면 주문 롤백 후 트랜잭션 밖에서 배송을 보상한다")
    void compensatesFlushFailureAfterRollback() {
        Order existing = orderRepository.saveAndFlush(existingOrder(null));
        stubExternalServices();
        assertRollbackBeforeCompensation();

        assertThatThrownBy(() -> service.create(user, command(null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR);

        verify(deliveryGateway).deleteByOrderId(createdOrderId.get());
        assertThat(orderRepository.findById(existing.getId())).isPresent();
        verifyNoInteractions(notificationSender);
    }

    @Test
    @DisplayName("flush가 성공해도 지연 제약으로 커밋이 실패하면 주문을 롤백하고 배송을 보상한다")
    void compensatesFailureAtCommit() {
        String requestNote = "commit-failure-" + UUID.randomUUID();
        // 배송 ID는 다르게 두어 INSERT/flush는 성공하고, 아래 지연 제약이 커밋에서만 실패하게 한다.
        Order existing = Order.create(UUID.randomUUID(), user.userId(), supplier.companyId(), receiver.companyId(),
                product.productId(), product.name(), product.price(), 1, UUID.randomUUID(), requestNote);
        orderRepository.saveAndFlush(existing);
        jdbcTemplate.execute("""
                ALTER TABLE order_schema.p_orders
                ADD CONSTRAINT uk_order_test_deferred_note UNIQUE (orderer_id, request_note)
                DEFERRABLE INITIALLY DEFERRED
                """);
        try {
            stubExternalServices();
            assertRollbackBeforeCompensation();

            assertThatThrownBy(() -> service.create(user, command(requestNote)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR);

            verify(deliveryGateway).deleteByOrderId(createdOrderId.get());
            assertThat(orderRepository.findById(existing.getId())).isPresent();
            verifyNoInteractions(notificationSender);
        } finally {
            jdbcTemplate.execute("ALTER TABLE order_schema.p_orders DROP CONSTRAINT uk_order_test_deferred_note");
        }
    }

    @Test
    @DisplayName("기존 DB 트랜잭션 안에서 주문 생성을 호출하면 외부 호출 전에 차단한다")
    void rejectsAnOuterTransaction() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(
                status -> service.create(user, command(null))))
                .isInstanceOf(IllegalTransactionStateException.class);
        verifyNoInteractions(companyReader, deliveryGateway);
    }

    @Test
    @DisplayName("저장과 이벤트 발행이 끝나도 커밋 전에는 알림을 제출하지 않는다")
    void notifiesOnlyAfterCommit() {
        Order order = existingOrder(null);
        transactionTemplate.executeWithoutResult(status -> {
            orderRepository.saveAndFlush(order);
            eventPublisher.publishEvent(OrderCreatedEvent.from(order, supplier.name(), receiver.name()));
            verifyNoInteractions(notificationSender);
        });

        verify(notificationSender).send(OrderCreatedEvent.from(order, supplier.name(), receiver.name()));
    }

    @Test
    @DisplayName("트랜잭션 밖에서 발행한 이벤트는 커밋된 주문으로 간주하지 않는다")
    void ignoresEventWithoutTransaction() {
        eventPublisher.publishEvent(OrderCreatedEvent.from(existingOrder(null), supplier.name(), receiver.name()));

        verifyNoInteractions(notificationSender);
    }

    private void stubExternalServices() {
        given(companyReader.getCompany(supplier.companyId())).willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return supplier;
        });
        given(companyReader.getCompany(receiver.companyId())).willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return receiver;
        });
        given(companyReader.getProduct(product.productId())).willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return product;
        });
        given(deliveryGateway.create(any())).willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            CreateDeliveryCommand command = invocation.getArgument(0);
            createdOrderId.set(command.orderId());
            return deliveryId;
        });
    }

    private void assertRollbackBeforeCompensation() {
        willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM order_schema.p_orders WHERE order_id = ?", Integer.class, createdOrderId.get()))
                    .isZero();
            return null;
        }).given(deliveryGateway).deleteByOrderId(any());
    }

    private CreateOrderCommand command(String requestNote) {
        return new CreateOrderCommand(supplier.companyId(), receiver.companyId(), product.productId(), 50, requestNote);
    }

    private Order existingOrder(String requestNote) {
        return Order.create(UUID.randomUUID(), UUID.randomUUID(), supplier.companyId(), receiver.companyId(),
                product.productId(), product.name(), product.price(), 1, deliveryId, requestNote);
    }
}
