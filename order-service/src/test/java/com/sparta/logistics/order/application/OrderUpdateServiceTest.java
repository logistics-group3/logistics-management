package com.sparta.logistics.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.order.application.dto.OrderResult;
import com.sparta.logistics.order.application.dto.UpdateOrderCommand;
import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.domain.OrderErrorCode;
import com.sparta.logistics.order.domain.OrderRepository;
import com.sparta.logistics.order.domain.OrderStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderUpdateServiceTest {

    @Mock
    private CompanyReader companyReader;
    @Mock
    private DeliveryGateway deliveryGateway;
    @Mock
    private OrderWriter orderWriter;
    @Mock
    private OrderRepository orderRepository;

    private final UUID ownerId = UUID.randomUUID();
    private final Order order = Order.create(UUID.randomUUID(), ownerId, UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), "주문 당시 상품", 12_000L, 50, UUID.randomUUID(), "기존 요청사항");
    private final UpdateOrderCommand command = new UpdateOrderCommand("오후에 도착해 주세요");
    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(companyReader, deliveryGateway, orderWriter, orderRepository);
    }

    @AfterEach
    void neverCreatesDeliveryOrPublishesCreationEvent() {
        verifyNoInteractions(companyReader, deliveryGateway, orderWriter);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("주문자 본인과 MASTER는 요청사항을 수정하고 기존 스냅샷을 반환한다")
    void ownerAndMasterCanUpdate(Role role) {
        given(orderRepository.findByIdForUpdate(order.getId())).willReturn(Optional.of(order));
        OrderResult before = OrderResult.from(order);

        OrderResult result = service.updateRequestNote(user(role), order.getId(), command);

        assertThat(result.requestNote()).isEqualTo(command.requestNote());
        assertThat(result).usingRecursiveComparison().ignoringFields("requestNote").isEqualTo(before);
    }

    @Test
    @DisplayName("타인 주문 수정은 403이며 기존 요청사항을 유지한다")
    void rejectsOtherOwner() {
        given(orderRepository.findByIdForUpdate(order.getId())).willReturn(Optional.of(order));
        UserContext other = new UserContext(UUID.randomUUID(), "other", Role.USER, false);

        assertThatThrownBy(() -> service.updateRequestNote(other, order.getId(), command))
                .extracting("errorCode").isEqualTo(CommonErrorCode.FORBIDDEN);
        assertThat(order.getRequestNote()).isEqualTo("기존 요청사항");
    }

    @Test
    @DisplayName("없는 주문은 ORDER_NOT_FOUND다")
    void rejectsMissingOrder() {
        given(orderRepository.findByIdForUpdate(order.getId())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRequestNote(user(Role.USER), order.getId(), command))
                .extracting("errorCode").isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("주문자와 MASTER도 취소된 주문은 수정할 수 없다")
    void rejectsCanceledOrder(Role role) {
        given(orderRepository.findByIdForUpdate(order.getId())).willReturn(Optional.of(order));
        ReflectionTestUtils.setField(order, "status", OrderStatus.CANCELED);

        assertThatThrownBy(() -> service.updateRequestNote(user(role), order.getId(), command))
                .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_STATUS_TRANSITION);
        assertThat(order.getRequestNote()).isEqualTo("기존 요청사항");
    }

    @Test
    @DisplayName("타인 주문은 취소 여부를 검사하기 전에 권한을 거부한다")
    void checksOwnerBeforeStatus() {
        given(orderRepository.findByIdForUpdate(order.getId())).willReturn(Optional.of(order));
        ReflectionTestUtils.setField(order, "status", OrderStatus.CANCELED);
        UserContext other = new UserContext(UUID.randomUUID(), "other", Role.USER, false);

        assertThatThrownBy(() -> service.updateRequestNote(other, order.getId(), command))
                .extracting("errorCode").isEqualTo(CommonErrorCode.FORBIDDEN);
    }

    private UserContext user(Role role) {
        return new UserContext(role == Role.MASTER ? UUID.randomUUID() : ownerId, "editor", role, false);
    }
}
