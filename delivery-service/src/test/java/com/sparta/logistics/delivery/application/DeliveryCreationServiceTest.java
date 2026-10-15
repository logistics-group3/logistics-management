package com.sparta.logistics.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.delivery.DeliveryFixture;
import com.sparta.logistics.delivery.application.dto.CreateDeliveryCommand;
import com.sparta.logistics.delivery.application.dto.CreateDeliveryResult;
import com.sparta.logistics.delivery.application.dto.ActiveRouteCount;
import com.sparta.logistics.delivery.domain.Delivery;
import com.sparta.logistics.delivery.domain.DeliveryErrorCode;
import com.sparta.logistics.delivery.domain.DeliveryRepository;
import com.sparta.logistics.delivery.domain.DeliveryRoute;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class DeliveryCreationServiceTest {

    private static final UUID SUPPLIER_HUB = UUID.randomUUID();
    private static final UUID RECEIVER_HUB = UUID.randomUUID();
    private static final UUID MANAGER_A = UUID.fromString("a0000000-0000-0000-0000-000000000000");
    private static final UUID MANAGER_B = UUID.fromString("b0000000-0000-0000-0000-000000000000");
    private static final UUID MANAGER_C = UUID.fromString("c0000000-0000-0000-0000-000000000000");

    @Mock
    private DeliveryRepository deliveryRepository;
    @Mock
    private DeliveryQueryRepository deliveryQueryRepository;
    @Mock
    private HubRouteReader hubRouteReader;
    @Mock
    private DeliveryManagerReader deliveryManagerReader;
    @Mock
    private RecipientReader recipientReader;
    @InjectMocks
    private DeliveryCreationService deliveryCreationService;

    @Test
    @DisplayName("구간 담당 허브에 배송담당자가 없으면 DELIVERY_MANAGER_NOT_FOUND이고 아무것도 저장하지 않는다")
    void noDeliveryManager() {
        CreateDeliveryCommand command = command(SUPPLIER_HUB, RECEIVER_HUB);
        given(deliveryRepository.findByOrderId(command.orderId())).willReturn(Optional.empty());
        given(hubRouteReader.getExpectedRoute(SUPPLIER_HUB, RECEIVER_HUB)).willReturn(DeliveryFixture.HUB_ROUTE);
        given(deliveryManagerReader.findIdsByHub(SUPPLIER_HUB)).willReturn(List.of(MANAGER_A));
        given(deliveryManagerReader.findIdsByHub(RECEIVER_HUB)).willReturn(List.of());

        assertThatThrownBy(() -> deliveryCreationService.create(command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(DeliveryErrorCode.DELIVERY_MANAGER_NOT_FOUND);
        then(deliveryRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("담당 건수가 가장 적은 후보를 배정하고, 같은 배송의 집하 배정을 세어 허브 이동은 다음 후보가 받는다")
    void assignsLeastLoadedCountingSameDelivery() {
        CreateDeliveryCommand command = command(SUPPLIER_HUB, RECEIVER_HUB);
        givenNoExistingDelivery(command);
        given(hubRouteReader.getExpectedRoute(SUPPLIER_HUB, RECEIVER_HUB)).willReturn(DeliveryFixture.HUB_ROUTE);
        given(deliveryManagerReader.findIdsByHub(SUPPLIER_HUB)).willReturn(List.of(MANAGER_B, MANAGER_A));
        given(deliveryManagerReader.findIdsByHub(RECEIVER_HUB)).willReturn(List.of(MANAGER_C));
        // A는 담당 1건, B는 0건: 집하는 B. B가 1건이 되어 A와 동점이므로 허브 이동은 userId가 앞선 A
        given(deliveryQueryRepository.countActiveRoutes(anyCollection())).willReturn(List.of(new ActiveRouteCount(MANAGER_A, 1L)));
        given(recipientReader.findSlackId(command.receiverManagerId())).willReturn(Optional.of("U02BCDEF3GH"));

        CreateDeliveryResult result = deliveryCreationService.create(command);

        Delivery saved = savedDelivery();
        assertThat(result.created()).isTrue();
        assertThat(saved.getRoutes()).extracting(DeliveryRoute::getManagerId).containsExactly(MANAGER_B, MANAGER_A, MANAGER_C);
        assertThat(saved.getRecipientSlackId()).isEqualTo("U02BCDEF3GH");
    }

    @Test
    @DisplayName("같은 허브면 I-03을 부르지 않고 I-14도 한 번만 불러 2구간을 만든다")
    void sameHub() {
        CreateDeliveryCommand command = command(SUPPLIER_HUB, SUPPLIER_HUB);
        givenNoExistingDelivery(command);
        given(deliveryManagerReader.findIdsByHub(SUPPLIER_HUB)).willReturn(List.of(MANAGER_A));
        given(deliveryQueryRepository.countActiveRoutes(anyCollection())).willReturn(List.of());
        given(recipientReader.findSlackId(command.receiverManagerId())).willReturn(Optional.empty());

        deliveryCreationService.create(command);

        Delivery saved = savedDelivery();
        assertThat(saved.getRoutes()).hasSize(2);
        assertThat(saved.getRecipientSlackId()).isNull();
        then(hubRouteReader).shouldHaveNoInteractions();
        then(deliveryManagerReader).should().findIdsByHub(SUPPLIER_HUB);
    }

    @Test
    @DisplayName("같은 orderId 배송이 이미 있으면 다른 서비스를 부르지 않고 기존 배송을 돌려준다")
    void existingOrder() {
        Delivery existing = DeliveryFixture.threeRoutes();
        CreateDeliveryCommand command = command(SUPPLIER_HUB, RECEIVER_HUB);
        given(deliveryRepository.findByOrderId(command.orderId())).willReturn(Optional.of(existing));

        CreateDeliveryResult result = deliveryCreationService.create(command);

        assertThat(result.created()).isFalse();
        assertThat(result.delivery().deliveryId()).isEqualTo(existing.getId());
        then(deliveryManagerReader).shouldHaveNoInteractions();
        then(deliveryRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("저장 시점에 order_id 유니크 위반(동시 요청)이면 먼저 저장된 배송을 돌려준다")
    void concurrentDuplicate() {
        Delivery winner = DeliveryFixture.twoRoutes();
        CreateDeliveryCommand command = command(SUPPLIER_HUB, SUPPLIER_HUB);
        given(deliveryRepository.findByOrderId(command.orderId())).willReturn(Optional.empty(), Optional.of(winner));
        given(deliveryManagerReader.findIdsByHub(SUPPLIER_HUB)).willReturn(List.of(MANAGER_A));
        given(deliveryQueryRepository.countActiveRoutes(anyCollection())).willReturn(List.of());
        given(recipientReader.findSlackId(command.receiverManagerId())).willReturn(Optional.empty());
        given(deliveryRepository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("uk_p_deliveries_order_id"));

        CreateDeliveryResult result = deliveryCreationService.create(command);

        assertThat(result.created()).isFalse();
        assertThat(result.delivery().deliveryId()).isEqualTo(winner.getId());
    }

    private void givenNoExistingDelivery(CreateDeliveryCommand command) {
        given(deliveryRepository.findByOrderId(command.orderId())).willReturn(Optional.empty());
        given(deliveryRepository.saveAndFlush(any())).willAnswer(invocation -> invocation.getArgument(0));
    }

    private Delivery savedDelivery() {
        ArgumentCaptor<Delivery> captor = ArgumentCaptor.forClass(Delivery.class);
        then(deliveryRepository).should().saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private static CreateDeliveryCommand command(UUID departureHub, UUID destinationHub) {
        return new CreateDeliveryCommand(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), departureHub,
                destinationHub, "공급업체 주소", "요청업체 주소", "요청업체", UUID.randomUUID());
    }
}
