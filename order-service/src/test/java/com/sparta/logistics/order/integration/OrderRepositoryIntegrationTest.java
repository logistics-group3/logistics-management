package com.sparta.logistics.order.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.logistics.common.auth.UserHeaders;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.domain.OrderRepository;
import com.sparta.logistics.order.domain.OrderStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@TestPropertySource(properties = {
        "spring.flyway.schemas=order_schema",
        "spring.jpa.properties.hibernate.default_schema=order_schema"
})
class OrderRepositoryIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("선발급 주문 ID와 스냅샷을 실제 DB에 저장하고 새 트랜잭션에서 읽는다")
    void savesAndReloadsOrder() {
        Order order = order(UUID.randomUUID());
        Order saved = orderRepository.saveAndFlush(order);

        Order found = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(saved.getId()).isEqualTo(order.getId());
        assertThat(found.getProductName()).isEqualTo("마른오징어");
        assertThat(found.getUnitPrice()).isEqualTo(12_000L);
        assertThat(found.getQuantity()).isEqualTo(50);
        assertThat(found.getTotalPrice()).isEqualTo(600_000L);
        assertThat(found.getDeliveryId()).isEqualTo(order.getDeliveryId());
        assertThat(found.getStatus()).isEqualTo(OrderStatus.ORDERED);
        assertThat(found.getRequestNote()).isNull();
        assertThat(found.getCreatedBy()).isEqualTo("SYSTEM");
        assertThat(found.getUpdatedBy()).isEqualTo("SYSTEM");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Gateway 사용자 헤더가 있으면 주문 생성자에 해당 username을 기록한다")
    void recordsRequestUsername() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(UserHeaders.USERNAME, "junhyun");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        UUID orderId;
        try {
            orderId = orderRepository.saveAndFlush(order(UUID.randomUUID())).getId();
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        assertThat(orderRepository.findById(orderId).orElseThrow().getCreatedBy()).isEqualTo("junhyun");
    }

    @Test
    @DisplayName("주문을 논리 삭제하면 조회에서 제외하지만 원본과 삭제 기록은 DB에 남는다")
    void hidesSoftDeletedOrder() {
        Order saved = orderRepository.saveAndFlush(order(UUID.randomUUID()));

        transactionTemplate.executeWithoutResult(status ->
                orderRepository.findById(saved.getId()).orElseThrow().softDelete("master01"));

        assertThat(orderRepository.findById(saved.getId())).isEmpty();
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT deleted_at, deleted_by, total_price FROM order_schema.p_orders WHERE order_id = ?", saved.getId());
        assertThat(row.get("deleted_at")).isNotNull();
        assertThat(row.get("deleted_by")).isEqualTo("master01");
        assertThat(row.get("total_price")).isEqualTo(600_000L);
    }

    @Test
    @DisplayName("없는 주문을 조회하면 빈 Optional을 반환한다")
    void returnsEmptyForMissingOrder() {
        assertThat(orderRepository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("활성 주문 두 건에 같은 배송을 연결하면 DB가 두 번째 저장을 거부한다")
    void rejectsDuplicateActiveDelivery() {
        UUID deliveryId = UUID.randomUUID();
        Order first = orderRepository.saveAndFlush(order(deliveryId));

        assertThatThrownBy(() -> orderRepository.saveAndFlush(order(deliveryId)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_p_orders_delivery_id");
        assertThat(orderRepository.findById(first.getId())).isPresent();
    }

    @Test
    @DisplayName("배송 유니크 제약은 SA 5.1에 따라 논리 삭제된 주문을 제외한다")
    void appliesDeliveryUniquenessOnlyToActiveOrders() {
        UUID deliveryId = UUID.randomUUID();
        Order first = orderRepository.saveAndFlush(order(deliveryId));
        transactionTemplate.executeWithoutResult(status ->
                orderRepository.findById(first.getId()).orElseThrow().softDelete("master01"));

        Order second = orderRepository.saveAndFlush(order(deliveryId));

        assertThat(orderRepository.findById(first.getId())).isEmpty();
        assertThat(orderRepository.findById(second.getId())).isPresent();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM order_schema.p_orders WHERE delivery_id = ?", Integer.class, deliveryId))
                .isEqualTo(2);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(value = {
            "quantity = 0|ck_p_orders_quantity",
            "unit_price = -1|ck_p_orders_unit_price",
            "total_price = -1|ck_p_orders_total_price",
            "status = 'DELIVERED'|ck_p_orders_status",
            "receiver_company_id = supplier_company_id|ck_p_orders_different_companies"
    }, delimiter = '|')
    @DisplayName("Entity 검증을 우회한 잘못된 데이터도 PostgreSQL CHECK가 거부한다")
    void enforcesCheckConstraints(String assignment, String constraint) {
        Order saved = orderRepository.saveAndFlush(order(UUID.randomUUID()));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE order_schema.p_orders SET " + assignment + " WHERE order_id = ?", saved.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
    }

    @ParameterizedTest
    @ValueSource(strings = {"orderer_id", "supplier_company_id", "receiver_company_id", "product_id", "delivery_id"})
    @DisplayName("외부 리소스를 FK 없이 참조하더라도 ID는 필수다")
    void requiresReferenceIds(String column) {
        Order saved = orderRepository.saveAndFlush(order(UUID.randomUUID()));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE order_schema.p_orders SET " + column + " = NULL WHERE order_id = ?", saved.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(column);
    }

    @Test
    @DisplayName("Flyway V1은 주문 스키마에만 적용되고 서비스 간 FK는 만들지 않는다")
    void migratesOrderSchemaWithoutCrossServiceForeignKeys() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM order_schema.flyway_schema_history WHERE version = '1' AND success", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT to_regclass('public.p_orders')::text", String.class)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conrelid = 'order_schema.p_orders'::regclass AND contype = 'f'",
                Integer.class)).isZero();
    }

    @Test
    @DisplayName("주문 조회용 인덱스는 삭제되지 않은 행만 포함한다")
    void createsPartialIndexes() {
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList(
                "SELECT indexname, indexdef FROM pg_indexes WHERE schemaname = 'order_schema' AND tablename = 'p_orders'");
        for (String name : List.of("idx_p_orders_orderer_id", "idx_p_orders_supplier_company_id",
                "idx_p_orders_receiver_company_id", "idx_p_orders_product_id", "idx_p_orders_created_at",
                "uk_p_orders_delivery_id")) {
            assertThat(indexes).filteredOn(row -> name.equals(row.get("indexname")))
                    .singleElement().satisfies(row ->
                            assertThat((String) row.get("indexdef")).contains("WHERE (deleted_at IS NULL)"));
        }
    }

    private static Order order(UUID deliveryId) {
        return Order.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "마른오징어", 12_000L, 50, deliveryId, null);
    }
}
