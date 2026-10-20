package com.sparta.logistics.order.application.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.order.application.CompanyReader;
import com.sparta.logistics.order.application.OrderNotificationSender;
import com.sparta.logistics.order.application.dto.CompanyResult;
import com.sparta.logistics.order.application.event.OrderCreatedEvent;
import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.domain.OrderRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssignmentNotificationHandlerTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CompanyReader companyReader;
    @Mock
    private DeliveryManagerDmService deliveryManagerDmService;
    @Mock
    private OrderNotificationSender orderNotificationSender;
    private AssignmentNotificationHandler handler;

    private final UUID supplierId = UUID.randomUUID();
    private final UUID receiverId = UUID.randomUUID();
    private final UUID deliveryId = UUID.randomUUID();
    private Order order;

    @BeforeEach
    void setUp() {
        handler = new AssignmentNotificationHandler(orderRepository, companyReader, deliveryManagerDmService, orderNotificationSender);
        order = Order.create(UUID.randomUUID(), UUID.randomUUID(), supplierId, receiverId, UUID.randomUUID(), "마른오징어",
                12_000L, 3, deliveryId, "오전 도착");
    }

    @Test
    @DisplayName("배정 완료면 주문 정보와 업체명을 다시 읽어 담당자 DM을 보낸다")
    void assignedSendsDm() {
        given(orderRepository.findById(order.getId())).willReturn(Optional.of(order));
        given(companyReader.getCompany(supplierId)).willReturn(company(supplierId, "공급업체"));
        given(companyReader.getCompany(receiverId)).willReturn(company(receiverId, "수령업체"));

        handler.handle(new AssignmentNotification(order.getId(), deliveryId, AssignmentNotification.Type.ASSIGNED));

        ArgumentCaptor<OrderCreatedEvent> event = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        then(deliveryManagerDmService).should().notifyManagers(event.capture());
        assertThat(event.getValue()).isEqualTo(new OrderCreatedEvent(order.getId(), "공급업체", "수령업체", "마른오징어", 3,
                "오전 도착", deliveryId));
        then(orderNotificationSender).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("업체명을 읽지 못해도 DM은 업체명 없이 보낸다")
    void assignedWithoutCompanyNames() {
        given(orderRepository.findById(order.getId())).willReturn(Optional.of(order));
        given(companyReader.getCompany(any())).willThrow(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE));

        handler.handle(new AssignmentNotification(order.getId(), deliveryId, AssignmentNotification.Type.ASSIGNED));

        ArgumentCaptor<OrderCreatedEvent> event = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        then(deliveryManagerDmService).should().notifyManagers(event.capture());
        assertThat(event.getValue().supplierCompanyName()).isEqualTo(AssignmentNotificationHandler.UNKNOWN_COMPANY);
    }

    @Test
    @DisplayName("수동 지정 필요면 MASTER 채널에 지정을 요청하고 담당자 DM은 보내지 않는다")
    void manualRequiredAlertsMaster() {
        given(orderRepository.findById(order.getId())).willReturn(Optional.of(order));

        handler.handle(new AssignmentNotification(order.getId(), deliveryId, AssignmentNotification.Type.MANUAL_REQUIRED));

        then(orderNotificationSender).should().sendManualAssignmentRequired(order.getId(), deliveryId);
        then(deliveryManagerDmService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("취소된 주문, 없는 주문, 배송이 다른 알림은 아무것도 보내지 않는다")
    void skipsInactiveOrders() {
        Order canceled = Order.create(UUID.randomUUID(), UUID.randomUUID(), supplierId, receiverId, UUID.randomUUID(), "상품",
                1_000L, 1, deliveryId, null);
        canceled.cancel();
        UUID missing = UUID.randomUUID();
        given(orderRepository.findById(canceled.getId())).willReturn(Optional.of(canceled));
        given(orderRepository.findById(missing)).willReturn(Optional.empty());
        given(orderRepository.findById(order.getId())).willReturn(Optional.of(order));

        handler.handle(new AssignmentNotification(canceled.getId(), deliveryId, AssignmentNotification.Type.ASSIGNED));
        handler.handle(new AssignmentNotification(missing, deliveryId, AssignmentNotification.Type.MANUAL_REQUIRED));
        handler.handle(new AssignmentNotification(order.getId(), UUID.randomUUID(), AssignmentNotification.Type.ASSIGNED));

        then(deliveryManagerDmService).should(never()).notifyManagers(any());
        then(orderNotificationSender).shouldHaveNoInteractions();
        then(companyReader).shouldHaveNoInteractions();
    }

    private static CompanyResult company(UUID id, String name) {
        return new CompanyResult(id, name, UUID.randomUUID(), "주소", UUID.randomUUID());
    }
}
