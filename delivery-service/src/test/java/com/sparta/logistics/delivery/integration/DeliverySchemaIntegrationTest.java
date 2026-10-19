package com.sparta.logistics.delivery.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.logistics.common.support.PostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V1 마이그레이션의 CHECK 제약 검증. Hibernate validate는 CHECK를 보지 않으므로 SQL로 직접 넣어 본다(migration 규칙).
 * Java enum을 거치지 않는 경로(직접 SQL, 초기 데이터)에서 잘못된 값이 들어오는 것을 DB가 막는지 확인한다.
 */
class DeliverySchemaIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("배송 상태에 정의되지 않은 값을 넣으면 DB가 거부한다")
    void rejectsUnknownDeliveryStatus() {
        assertThatThrownBy(() -> insertDelivery(UUID.randomUUID(), "DELIVERD"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_p_deliveries_status");
    }

    @Test
    @DisplayName("구간 유형이나 구간 상태에 정의되지 않은 값을 넣으면 DB가 거부한다")
    void rejectsUnknownRouteTypeAndStatus() {
        UUID deliveryId = UUID.randomUUID();
        insertDelivery(deliveryId, "PICKUP_WAITING");

        assertThatThrownBy(() -> insertRoute(deliveryId, 1, "TRUCK", "WAITING"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_p_delivery_routes_route_type");
        assertThatThrownBy(() -> insertRoute(deliveryId, 1, "PICKUP", "DONE"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_p_delivery_routes_status");
    }

    private void insertDelivery(UUID deliveryId, String status) {
        jdbcTemplate.update("""
                INSERT INTO p_deliveries (delivery_id, status, pickup_address, delivery_address, recipient_name,
                    order_id, supplier_company_id, receiver_company_id, departure_hub_id, destination_hub_id,
                    created_at, created_by, updated_at, updated_by)
                VALUES (?, ?, '공급업체 주소', '요청업체 주소', '수령인', ?, ?, ?, ?, ?, now(), 'test', now(), 'test')
                """, deliveryId, status, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID());
    }

    private void insertRoute(UUID deliveryId, int sequence, String routeType, String status) {
        jdbcTemplate.update("""
                INSERT INTO p_delivery_routes (delivery_route_id, sequence, route_type, status, delivery_id, manager_id,
                    managing_hub_id, created_at, created_by, updated_at, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, now(), 'test', now(), 'test')
                """, UUID.randomUUID(), sequence, routeType, status, deliveryId, UUID.randomUUID(), UUID.randomUUID());
    }
}
