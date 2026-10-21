package com.sparta.logistics.order.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
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
import com.sparta.logistics.order.application.dto.OrderResult;
import com.sparta.logistics.order.application.dto.ProductResult;
import com.sparta.logistics.order.domain.CreationFailureReason;
import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.domain.OrderCreationRepository;
import com.sparta.logistics.order.domain.OrderErrorCode;
import com.sparta.logistics.order.domain.OrderRepository;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@TestPropertySource(properties = {
        "spring.flyway.schemas=order_schema", "spring.jpa.properties.hibernate.default_schema=order_schema",
        "spring.jpa.open-in-view=false", "slack.webhook-url="
})
class OrderIdempotencyIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private OrderService service;
    @Autowired
    private OrderWriter writer;
    @Autowired
    private OrderRepository orders;
    @Autowired
    private OrderCreationRepository creations;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
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
    private final CreateOrderCommand command = new CreateOrderCommand(supplierId, receiverId, productId, 2, "최초 요청사항");
    private final Set<UUID> fixtureIds = ConcurrentHashMap.newKeySet();

    @BeforeEach
    void configureExternalServices() {
        given(company.getCompany(supplierId)).willReturn(new CompanyResult(supplierId, "공급", UUID.randomUUID(), "서울", UUID.randomUUID()));
        given(company.getCompany(receiverId)).willReturn(new CompanyResult(receiverId, "수령", UUID.randomUUID(), "부산", user.userId()));
        given(company.getProduct(productId)).willReturn(new ProductResult(productId, supplierId, "상품", 12_000L));
        given(delivery.create(any())).willAnswer(invocation -> {
            CreateDeliveryCommand request = invocation.getArgument(0);
            fixtureIds.add(request.orderId());
            return UUID.randomUUID();
        });
    }

    @AfterEach
    void cleanUp() {
        fixtureIds.forEach(id -> {
            jdbc.update("DELETE FROM order_schema.p_order_creations WHERE order_id=?", id);
            jdbc.update("DELETE FROM order_schema.p_orders WHERE order_id=?", id);
        });
    }

    @Test
    @DisplayName("완료된 키를 다시 보내면 JSONB의 최초 결과를 반환하고 주문·배송·알림은 한 건이다")
    void replaysPersistedResponse() {
        OrderResult first = service.create(user, "same-key", command);

        OrderResult replay = service.create(user, "same-key", command);

        assertThat(replay).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT jsonb_typeof(response_snapshot) FROM order_schema.p_order_creations WHERE order_id=?",
                String.class, first.orderId())).isEqualTo("object");
        verify(delivery, times(1)).create(any());
        verify(notification, times(1)).send(any());
        verify(company, times(1)).getProduct(productId);
        assertThat(countRecords()).isEqualTo(1);
    }

    @Test
    @DisplayName("원본 주문이 수정·취소·논리 삭제돼도 같은 키는 새 주문 대신 최초 응답을 반환한다")
    void replaysOriginalAfterOrderChanges() {
        OrderResult first = service.create(user, "same-key", command);
        transactions.executeWithoutResult(status -> {
            Order order = orders.findByIdForUpdate(first.orderId()).orElseThrow();
            order.updateRequestNote("수정된 요청사항");
            order.cancel();
            order.softDelete("fixture");
        });

        assertThat(service.create(user, "same-key", command)).isEqualTo(first);
        assertThat(orders.findById(first.orderId())).isEmpty();
        verify(delivery, times(1)).create(any());
        verify(notification, times(1)).send(any());
    }

    @Test
    @DisplayName("같은 키의 다른 본문은 409이며 기존 주문을 수정하거나 새로 생성하지 않는다")
    void rejectsDifferentPayload() {
        OrderResult first = service.create(user, "same-key", command);
        CreateOrderCommand different = new CreateOrderCommand(supplierId, receiverId, productId, 3, command.requestNote());

        assertThatThrownBy(() -> service.create(user, "same-key", different))
                .extracting("errorCode").isEqualTo(OrderErrorCode.ORDER_IDEMPOTENCY_KEY_CONFLICT);
        assertThat(orders.findById(first.orderId()).orElseThrow().getQuantity()).isEqualTo(2);
        verify(delivery, times(1)).create(any());
    }

    @Test
    @DisplayName("다른 사용자의 같은 키와 같은 사용자의 대소문자가 다른 키는 독립된 주문이다")
    void scopesKeyToUserAndCase() {
        OrderResult first = service.create(user, "same-key", command);
        OrderResult differentCase = service.create(user, "SAME-KEY", command);
        UserContext master = new UserContext(UUID.randomUUID(), "master", Role.MASTER, false);
        OrderResult differentUser = service.create(master, "same-key", command);

        assertThat(Set.of(first.orderId(), differentCase.orderId(), differentUser.orderId())).hasSize(3);
        assertThat(differentUser.ordererId()).isEqualTo(master.userId());
        verify(delivery, times(3)).create(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PROCESSING", "ABORTED", "RECOVERY_REQUIRED"})
    @DisplayName("처리 중·실패 종료·복구 대기 키는 외부 서비스를 호출하지 않고 구분된 409를 반환한다")
    void refusesAutomaticReexecution(String state) {
        OrderCreationAttempt attempt = start("same-key");
        OrderErrorCode code = switch (state) {
            case "ABORTED" -> { writer.abort(attempt, null, null, null); yield OrderErrorCode.ORDER_CREATION_ABORTED; }
            case "RECOVERY_REQUIRED" -> {
                writer.requireRecovery(attempt, null, CreationFailureReason.DELIVERY_RESULT_UNKNOWN);
                yield OrderErrorCode.ORDER_CREATION_RECOVERY_REQUIRED;
            }
            default -> OrderErrorCode.ORDER_CREATION_IN_PROGRESS;
        };

        assertThatThrownBy(() -> service.create(user, "same-key", command)).extracting("errorCode").isEqualTo(code);
        verifyNoInteractions(company, delivery, notification);
    }

    @Test
    @DisplayName("진행 기록을 논리 삭제해도 키가 반환되지 않고 DB 유니크가 재사용을 막는다")
    void preservesDeletedKey() {
        OrderCreationAttempt attempt = start("same-key");
        transactions.executeWithoutResult(status -> creations.findByOrderIdForUpdate(attempt.orderId()).orElseThrow().softDelete("fixture"));

        assertThatThrownBy(() -> service.create(user, "same-key", command))
                .extracting("errorCode").isEqualTo(OrderErrorCode.ORDER_CREATION_RECOVERY_REQUIRED);
        assertThatThrownBy(() -> writer.start(new OrderCreationAttempt(UUID.randomUUID(), UUID.randomUUID(), user.userId()), command.identity("same-key")))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uk_p_order_creations_user_key");
        verifyNoInteractions(company, delivery, notification);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("동시 첫 요청의 INSERT 경쟁에서도 한 요청만 주문·배송·알림을 만들고 패자는 재조회한다")
    void arbitratesConcurrentFirstRequests(boolean differentPayload) throws Exception {
        CyclicBarrier beforeClaim = new CyclicBarrier(2);
        given(company.getProduct(productId)).willAnswer(invocation -> {
            beforeClaim.await(5, TimeUnit.SECONDS);
            return new ProductResult(productId, supplierId, "상품", 12_000L);
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CreateOrderCommand second = differentPayload
                    ? new CreateOrderCommand(supplierId, receiverId, productId, 3, command.requestNote()) : command;
            Future<Object> a = executor.submit(() -> createOrError(command));
            Future<Object> b = executor.submit(() -> createOrError(second));
            List<Object> results = List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));

            List<OrderResult> successes = results.stream().filter(OrderResult.class::isInstance).map(OrderResult.class::cast).toList();
            assertThat(successes).isNotEmpty();
            assertThat(successes).extracting(OrderResult::orderId).containsOnly(successes.get(0).orderId());
            if (differentPayload) {
                assertThat(results).contains(OrderErrorCode.ORDER_IDEMPOTENCY_KEY_CONFLICT);
            } else {
                assertThat(results.stream().filter(value -> !(value instanceof OrderResult)).toList())
                        .allMatch(value -> value == OrderErrorCode.ORDER_CREATION_IN_PROGRESS);
            }
            assertThat(countRecords()).isEqualTo(1);
            verify(delivery, times(1)).create(any());
            verify(notification, times(1)).send(any());
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"request_hash=NULL", "idempotency_key=NULL", "request_hash='bad'",
            "response_snapshot='[]'::jsonb", "status='COMPLETED', delivery_id=gen_random_uuid()"})
    @DisplayName("DB CHECK가 키·지문 쌍과 완료 응답 누락·잘못된 JSON을 막는다")
    void validatesDatabaseContract(String assignment) {
        OrderCreationAttempt attempt = start("same-key");

        assertThatThrownBy(() -> jdbc.update("UPDATE order_schema.p_order_creations SET " + assignment + " WHERE order_id=?", attempt.orderId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("V3 완료 기록이 있는 스키마에 V4를 적용해도 기존 기록과 NULL 키를 보존한다")
    void migratesExistingRecords() throws Exception {
        String schema = "idempotency_migration_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = dataSource.getConnection(); Statement sql = connection.createStatement()) {
            String originalSchema = connection.getSchema();
            sql.execute("CREATE SCHEMA " + schema);
            try {
                connection.setSchema(schema);
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V3__create_p_order_creations.sql"));
                sql.execute("INSERT INTO p_order_creations (order_creation_id,status,order_id,orderer_id,delivery_id,created_at,created_by,updated_at,updated_by) "
                        + "VALUES (gen_random_uuid(),'COMPLETED',gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),now(),'legacy',now(),'legacy')");
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V4__add_order_idempotency.sql"));
                try (ResultSet row = sql.executeQuery("SELECT status,idempotency_key,request_hash,response_snapshot,created_by FROM p_order_creations")) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getString(1)).isEqualTo("COMPLETED");
                    assertThat(row.getString(2)).isNull();
                    assertThat(row.getString(3)).isNull();
                    assertThat(row.getString(4)).isNull();
                    assertThat(row.getString(5)).isEqualTo("legacy");
                }
            } finally {
                connection.setSchema(originalSchema);
                sql.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    private Object createOrError(CreateOrderCommand request) {
        try { return service.create(user, "same-key", request); }
        catch (BusinessException exception) { return exception.getErrorCode(); }
    }

    private int countRecords() {
        return jdbc.queryForObject("SELECT count(*) FROM order_schema.p_order_creations WHERE orderer_id=?", Integer.class, user.userId());
    }

    private OrderCreationAttempt start(String key) {
        OrderCreationAttempt attempt = new OrderCreationAttempt(UUID.randomUUID(), UUID.randomUUID(), user.userId());
        fixtureIds.add(attempt.orderId());
        writer.start(attempt, command.identity(key));
        return attempt;
    }
}
