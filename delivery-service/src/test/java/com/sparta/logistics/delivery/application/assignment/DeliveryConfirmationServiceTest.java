package com.sparta.logistics.delivery.application.assignment;

import static com.sparta.logistics.delivery.DeliveryFixture.RECEIVER_HUB;
import static com.sparta.logistics.delivery.DeliveryFixture.SUPPLIER_HUB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.delivery.DeliveryFixture;
import com.sparta.logistics.delivery.application.DeliveryQueryRepository;
import com.sparta.logistics.delivery.domain.Delivery;
import com.sparta.logistics.delivery.domain.DeliveryErrorCode;
import com.sparta.logistics.delivery.domain.DeliveryRepository;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionOperations;

@ExtendWith(MockitoExtension.class)
class DeliveryConfirmationServiceTest {

    @Mock
    private DeliveryRepository deliveryRepository;
    @Mock
    private DeliveryQueryRepository deliveryQueryRepository;
    @Mock
    private AssignmentTrigger assignmentTrigger;
    @Mock
    private OrderCreationReader orderCreationReader;
    private DeliveryConfirmationService service;

    @BeforeEach
    void setUp() {
        service = new DeliveryConfirmationService(deliveryRepository, deliveryQueryRepository,
                TransactionOperations.withoutTransaction(), assignmentTrigger, orderCreationReader,
                new ConfirmationCheckPolicy(Duration.ofMinutes(2), Duration.ofMinutes(30), 50));
    }

    @Test
    @DisplayName("I-19 미확정 배송을 확정하고 배정 대기 구간의 담당 허브를 깨운다")
    void confirmRequestsAssignment() {
        Delivery delivery = DeliveryFixture.unassigned(SUPPLIER_HUB, RECEIVER_HUB);
        given(deliveryRepository.findByOrderIdForUpdate(delivery.getOrderId())).willReturn(Optional.of(delivery));

        service.confirm(delivery.getOrderId());

        assertThat(delivery.isConfirmed()).isTrue();
        then(assignmentTrigger).should().request(Set.of(SUPPLIER_HUB, RECEIVER_HUB));
    }

    @Test
    @DisplayName("I-19 이미 확정된 배송이면 같은 결과를 돌려주고 배정을 다시 깨우지 않는다(멱등)")
    void confirmIsIdempotent() {
        Delivery delivery = DeliveryFixture.unassigned(SUPPLIER_HUB, SUPPLIER_HUB);
        delivery.confirm();
        given(deliveryRepository.findByOrderIdForUpdate(delivery.getOrderId())).willReturn(Optional.of(delivery));

        assertThat(service.confirm(delivery.getOrderId()).orderId()).isEqualTo(delivery.getOrderId());

        then(assignmentTrigger).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("I-19 배송이 없거나 삭제됐으면 DELIVERY_NOT_FOUND")
    void confirmMissing() {
        UUID orderId = UUID.randomUUID();
        given(deliveryRepository.findByOrderIdForUpdate(orderId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(orderId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(DeliveryErrorCode.DELIVERY_NOT_FOUND);
        then(assignmentTrigger).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("안전망은 커밋된 주문의 배송만 확정하고, 처리 중이거나 실패한 주문의 배송은 확정하지 않는다")
    void confirmOverdueOnlyCommittedOrders() {
        Delivery committed = DeliveryFixture.unassigned(SUPPLIER_HUB, SUPPLIER_HUB);
        UUID processing = UUID.randomUUID();
        UUID aborted = UUID.randomUUID();
        given(deliveryQueryRepository.findUnconfirmedOrderIds(any(), any(), anyInt()))
                .willReturn(List.of(committed.getOrderId(), processing, aborted));
        given(orderCreationReader.getCreationResult(committed.getOrderId())).willReturn(new OrderCreationCheck("COMPLETED", "ORDERED"));
        given(orderCreationReader.getCreationResult(processing)).willReturn(new OrderCreationCheck("PROCESSING", null));
        given(orderCreationReader.getCreationResult(aborted)).willReturn(new OrderCreationCheck("ABORTED", null));
        given(deliveryRepository.findByOrderIdForUpdate(committed.getOrderId())).willReturn(Optional.of(committed));

        service.confirmOverdue();

        assertThat(committed.isConfirmed()).isTrue();
        then(deliveryRepository).should(never()).findByOrderIdForUpdate(processing);
        then(deliveryRepository).should(never()).findByOrderIdForUpdate(aborted);
    }

    @Test
    @DisplayName("안전망은 order가 응답하지 않으면 남은 건을 묻지 않고 이번 확인을 멈춘다")
    void confirmOverdueStopsWhenOrderUnavailable() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        given(deliveryQueryRepository.findUnconfirmedOrderIds(any(), any(), anyInt())).willReturn(List.of(first, second));
        given(orderCreationReader.getCreationResult(first)).willThrow(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE));

        service.confirmOverdue();

        then(orderCreationReader).should(never()).getCreationResult(second);
        then(deliveryRepository).shouldHaveNoInteractions();
    }
}
