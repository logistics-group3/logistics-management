package com.sparta.logistics.delivery.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.delivery.application.DeliveryManagerReader;
import com.sparta.logistics.delivery.application.DeliveryCreationService;
import com.sparta.logistics.delivery.application.DeliveryService;
import com.sparta.logistics.delivery.application.dto.CreateDeliveryCommand;
import com.sparta.logistics.common.exception.BusinessException;
import java.util.List;
import java.util.Optional;
import com.sparta.logistics.delivery.application.HubRouteReader;
import com.sparta.logistics.delivery.application.RecipientReader;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class DeliveryCreationRecoveryIntegrationTest extends PostgresIntegrationTest {
    @MockitoBean private HubRouteReader hubRouteReader;
    @MockitoBean private DeliveryManagerReader deliveryManagerReader;
    @MockitoBean private RecipientReader recipientReader;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DeliveryCreationService creation;
    @Autowired private DeliveryService service;

    @Test
    @DisplayName("배송이 없는 생성 시도를 조건부 종료하면 지연 생성도 막는 종료 표시가 남는다")
    void closesMissingDelivery() throws Exception {
        UUID orderId = UUID.randomUUID();
        mvc.perform(delete("/internal/deliveries/orders/{orderId}/creation", orderId))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM p_delivery_creation_closures WHERE order_id=?",
                Integer.class, orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("배송 정리와 종료 표시를 함께 커밋하고 반복 종료는 같은 결과다")
    void closesWaitingDeliveryAndRejectsLateCreation() {
        UUID hub = UUID.randomUUID();
        given(deliveryManagerReader.findIdsByHub(hub)).willReturn(List.of(UUID.randomUUID()));
        given(recipientReader.findSlackId(any())).willReturn(Optional.empty());
        UUID orderId = UUID.randomUUID();
        var command = new CreateDeliveryCommand(orderId, UUID.randomUUID(), UUID.randomUUID(), hub, hub,
                "테스트 집하지", "테스트 배송지", "테스트 수령인", UUID.randomUUID());
        UUID deliveryId = creation.create(command).delivery().deliveryId();
        service.closeCreation(orderId, deliveryId);
        service.closeCreation(orderId, deliveryId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM p_deliveries WHERE order_id=? AND deleted_at IS NULL",
                Integer.class, orderId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM p_delivery_routes WHERE delivery_id=? AND deleted_at IS NULL",
                Integer.class, deliveryId)).isZero();
        assertThatThrownBy(() -> creation.create(command)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("배송 ID가 바뀌었으면 종료 표시와 배송 삭제 모두 롤백한다")
    void mismatchedDeliveryLeavesNoClosure() {
        UUID hub = UUID.randomUUID();
        given(deliveryManagerReader.findIdsByHub(hub)).willReturn(List.of(UUID.randomUUID()));
        given(recipientReader.findSlackId(any())).willReturn(Optional.empty());
        UUID orderId = UUID.randomUUID();
        creation.create(new CreateDeliveryCommand(orderId, UUID.randomUUID(), UUID.randomUUID(), hub, hub,
                "테스트 집하지", "테스트 배송지", "테스트 수령인", UUID.randomUUID()));
        assertThatThrownBy(() -> service.closeCreation(orderId, UUID.randomUUID())).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM p_delivery_creation_closures WHERE order_id=?",
                Integer.class, orderId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM p_deliveries WHERE order_id=? AND deleted_at IS NULL",
                Integer.class, orderId)).isEqualTo(1);
    }
}
