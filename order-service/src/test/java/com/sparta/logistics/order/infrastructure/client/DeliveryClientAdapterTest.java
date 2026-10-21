package com.sparta.logistics.order.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.order.infrastructure.client.dto.DeliveryOrderStateResponse;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeliveryClientAdapterTest {
    private final DeliveryOrderStateClient stateClient = mock(DeliveryOrderStateClient.class);
    private final DeliveryClientAdapter adapter = new DeliveryClientAdapter(
            mock(DeliveryCreateClient.class), mock(DeliveryDeleteClient.class), mock(DeliveryCancelClient.class),
            mock(DeliveryCancelRevertClient.class), mock(DeliveryConfirmClient.class), stateClient);
    private final UUID orderId = UUID.randomUUID();

    @Test
    @DisplayName("CLOSED이고 배송 정보가 없으면 생성 종료로 본다")
    void closedWithoutDelivery() {
        given(stateClient.getByOrderId(orderId)).willReturn(new DeliveryOrderStateResponse(orderId, "CLOSED", null, null));

        assertThat(adapter.isCreationClosed(orderId)).isTrue();
    }

    @Test
    @DisplayName("ACTIVE와 NONE은 종료가 아니다")
    void notClosed() {
        given(stateClient.getByOrderId(orderId)).willReturn(new DeliveryOrderStateResponse(orderId, "NONE", null, null));

        assertThat(adapter.isCreationClosed(orderId)).isFalse();
    }

    @Test
    @DisplayName("CLOSED인데 활성 배송 정보가 함께 오면 운영 복구와 같이 거절한다")
    void contradictoryClosedResponseIsRejected() {
        given(stateClient.getByOrderId(orderId))
                .willReturn(new DeliveryOrderStateResponse(orderId, "CLOSED", UUID.randomUUID(), "PENDING"));

        assertThatThrownBy(() -> adapter.isCreationClosed(orderId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
        assertThatThrownBy(() -> adapter.getCreationState(orderId)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("다른 주문의 응답이면 거절한다")
    void otherOrderIsRejected() {
        given(stateClient.getByOrderId(orderId)).willReturn(new DeliveryOrderStateResponse(UUID.randomUUID(), "CLOSED", null, null));

        assertThatThrownBy(() -> adapter.isCreationClosed(orderId)).isInstanceOf(BusinessException.class);
    }
}
