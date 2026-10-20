package com.sparta.logistics.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.delivery.DeliveryFixture;
import com.sparta.logistics.delivery.application.dto.CreateDeliveryCommand;
import com.sparta.logistics.delivery.application.dto.CreateDeliveryResult;
import com.sparta.logistics.delivery.domain.Delivery;
import com.sparta.logistics.delivery.domain.DeliveryCreationClosureRepository;
import com.sparta.logistics.delivery.domain.DeliveryErrorCode;
import com.sparta.logistics.delivery.domain.DeliveryRepository;
import com.sparta.logistics.delivery.domain.DeliveryRoute;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionOperations;

@ExtendWith(MockitoExtension.class)
class DeliveryCreationServiceTest {

    private static final UUID SUPPLIER_HUB = UUID.randomUUID();
    private static final UUID RECEIVER_HUB = UUID.randomUUID();

    @Mock
    private DeliveryRepository deliveryRepository;
    @Mock
    private DeliveryCreationClosureRepository closureRepository;
    @Mock
    private HubRouteReader hubRouteReader;
    @Mock
    private RecipientReader recipientReader;
    private DeliveryCreationService deliveryCreationService;

    @BeforeEach
    void setUp() {
        deliveryCreationService = new DeliveryCreationService(deliveryRepository, closureRepository, hubRouteReader,
                recipientReader, TransactionOperations.withoutTransaction());
    }

    @Test
    @DisplayName("배송담당자 후보를 보지 않고 구간을 담당자 없이 미확정으로 저장한다. 배정은 주문 확정(I-19) 뒤에 한다")
    void savesUnconfirmedWithoutManagers() {
        CreateDeliveryCommand command = command(SUPPLIER_HUB, RECEIVER_HUB);
        givenNoExistingDelivery(command);
        given(hubRouteReader.getExpectedRoute(SUPPLIER_HUB, RECEIVER_HUB)).willReturn(DeliveryFixture.HUB_ROUTE);
        given(recipientReader.findSlackId(command.receiverManagerId())).willReturn(Optional.of("U02BCDEF3GH"));

        CreateDeliveryResult result = deliveryCreationService.create(command);

        Delivery saved = savedDelivery();
        assertThat(result.created()).isTrue();
        assertThat(saved.getRoutes()).hasSize(3).allMatch(DeliveryRoute::isAwaitingManager);
        assertThat(saved.getRoutes()).extracting(DeliveryRoute::getManagingHubId)
                .containsExactly(SUPPLIER_HUB, SUPPLIER_HUB, RECEIVER_HUB);
        assertThat(saved.getRecipientSlackId()).isEqualTo("U02BCDEF3GH");
        assertThat(saved.isConfirmed()).isFalse();
    }

    @Test
    @DisplayName("같은 허브면 I-03을 부르지 않고 2구간을 만든다")
    void sameHub() {
        CreateDeliveryCommand command = command(SUPPLIER_HUB, SUPPLIER_HUB);
        givenNoExistingDelivery(command);
        given(recipientReader.findSlackId(command.receiverManagerId())).willReturn(Optional.empty());

        deliveryCreationService.create(command);

        Delivery saved = savedDelivery();
        assertThat(saved.getRoutes()).hasSize(2);
        assertThat(saved.getRecipientSlackId()).isNull();
        then(hubRouteReader).shouldHaveNoInteractions();
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
        then(hubRouteReader).shouldHaveNoInteractions();
        then(deliveryRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("저장 시점에 order_id 유니크 위반(동시 요청)이면 먼저 저장된 배송을 돌려준다")
    void concurrentDuplicate() {
        Delivery winner = DeliveryFixture.twoRoutes();
        CreateDeliveryCommand command = command(SUPPLIER_HUB, SUPPLIER_HUB);
        // 처음 조회, 저장 트랜잭션 안의 재조회는 비어 있고, 유니크 위반 뒤 새 조회에서 먼저 저장된 배송이 보인다
        given(deliveryRepository.findByOrderId(command.orderId())).willReturn(Optional.empty(), Optional.empty(), Optional.of(winner));
        given(recipientReader.findSlackId(command.receiverManagerId())).willReturn(Optional.empty());
        given(deliveryRepository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("uk_p_deliveries_order_id"));

        CreateDeliveryResult result = deliveryCreationService.create(command);

        assertThat(result.created()).isFalse();
        assertThat(result.delivery().deliveryId()).isEqualTo(winner.getId());
    }

    @Test
    @DisplayName("종료된 시도(I-13 종료 표시)면 원격 호출 없이 DELIVERY_CREATION_CLOSED이고 저장하지 않는다")
    void closedBeforeRemoteCalls() {
        CreateDeliveryCommand command = command(SUPPLIER_HUB, RECEIVER_HUB);
        given(deliveryRepository.findByOrderId(command.orderId())).willReturn(Optional.empty());
        given(closureRepository.existsById(command.orderId())).willReturn(true);

        assertThatThrownBy(() -> deliveryCreationService.create(command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(DeliveryErrorCode.DELIVERY_CREATION_CLOSED);
        then(hubRouteReader).shouldHaveNoInteractions();
        then(recipientReader).shouldHaveNoInteractions();
        then(deliveryRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("원격 호출 중에 종료되면 저장 직전 잠금 안에서 확인해 DELIVERY_CREATION_CLOSED이고 저장하지 않는다")
    void closedWhileRemoteCallsRunning() {
        CreateDeliveryCommand command = command(SUPPLIER_HUB, SUPPLIER_HUB);
        given(deliveryRepository.findByOrderId(command.orderId())).willReturn(Optional.empty());
        given(recipientReader.findSlackId(command.receiverManagerId())).willReturn(Optional.empty());
        // 처음 확인은 없음, 잠금 안의 확인은 있음
        given(closureRepository.existsById(command.orderId())).willReturn(false, true);

        assertThatThrownBy(() -> deliveryCreationService.create(command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(DeliveryErrorCode.DELIVERY_CREATION_CLOSED);
        then(closureRepository).should().lockOrder(command.orderId());
        then(deliveryRepository).should(never()).saveAndFlush(any());
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
