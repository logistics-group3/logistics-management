package com.sparta.logistics.delivery.application;

import static com.sparta.logistics.delivery.DeliveryFixture.RECEIVER_HUB;
import static com.sparta.logistics.delivery.DeliveryFixture.RECEIVER_MANAGER;
import static com.sparta.logistics.delivery.DeliveryFixture.SUPPLIER_MANAGER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.delivery.DeliveryFixture;
import com.sparta.logistics.delivery.application.dto.ChangeRouteStatusCommand;
import com.sparta.logistics.delivery.application.dto.DeliveryResponse;
import com.sparta.logistics.delivery.application.dto.UpdateRecipientCommand;
import com.sparta.logistics.delivery.domain.Delivery;
import com.sparta.logistics.delivery.domain.DeliveryCreationClosureRepository;
import com.sparta.logistics.delivery.domain.DeliveryErrorCode;
import com.sparta.logistics.delivery.domain.DeliveryManager;
import com.sparta.logistics.delivery.domain.DeliveryRepository;
import com.sparta.logistics.delivery.domain.DeliveryStatus;
import com.sparta.logistics.delivery.domain.RouteStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.AuditorAware;
import org.springframework.transaction.support.TransactionOperations;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    private static final ChangeRouteStatusCommand MOVING = new ChangeRouteStatusCommand(RouteStatus.MOVING, null, null);

    @Mock
    private DeliveryRepository deliveryRepository;
    @Mock
    private DeliveryCreationClosureRepository closureRepository;
    @Mock
    private DeliveryManagerReader deliveryManagerReader;
    @Mock
    private AuditorAware<String> auditorAware;
    private DeliveryService deliveryService;

    private Delivery delivery;

    @BeforeEach
    void setUp() {
        deliveryService = new DeliveryService(deliveryRepository, closureRepository, deliveryManagerReader, auditorAware,
                TransactionOperations.withoutTransaction());
        delivery = DeliveryFixture.threeRoutes();
        // 담당자 조회가 먼저 실패하는 D-04 테스트에서는 배송을 읽지 않으므로 lenient
        lenient().when(deliveryRepository.findByIdForUpdate(delivery.getId())).thenReturn(Optional.of(delivery));
    }

    @Test
    @DisplayName("D-05 구간 담당자 본인이면 구간 상태를 바꾸고 배송 상태가 따라 바뀐다")
    void changeRouteStatusByManager() {
        DeliveryResponse result = deliveryService.changeRouteStatus(manager(SUPPLIER_MANAGER), delivery.getId(), routeId(0), MOVING);

        assertThat(result.delivery().status()).isEqualTo(DeliveryStatus.PICKING_UP);
        assertThat(result.routes().get(0).status()).isEqualTo(RouteStatus.MOVING);
    }

    @Test
    @DisplayName("D-05 그 구간의 담당자가 아니면 다른 구간 담당자여도 FORBIDDEN")
    void changeRouteStatusByOtherManager() {
        assertThatThrownBy(() -> deliveryService.changeRouteStatus(manager(RECEIVER_MANAGER), delivery.getId(), routeId(0), MOVING))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.FORBIDDEN);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PICKUP_WAITING);
    }

    @Test
    @DisplayName("D-04 재배정 대상의 소속 허브가 구간 담당 허브와 다르면 MANAGER_HUB_MISMATCH")
    void reassignHubMismatch() {
        UUID target = UUID.randomUUID();
        given(deliveryManagerReader.findById(target)).willReturn(Optional.of(new DeliveryManager(target, RECEIVER_HUB)));

        assertThatThrownBy(() -> deliveryService.reassignManager(delivery.getId(), routeId(1), target))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(DeliveryErrorCode.MANAGER_HUB_MISMATCH);
        assertThat(delivery.getRoutes().get(1).getManagerId()).isEqualTo(SUPPLIER_MANAGER);
    }

    @Test
    @DisplayName("D-04 재배정 대상이 없거나 배송담당자가 아니면 DELIVERY_MANAGER_NOT_FOUND")
    void reassignToNonManager() {
        UUID target = UUID.randomUUID();
        given(deliveryManagerReader.findById(target)).willReturn(Optional.empty());

        assertThatThrownBy(() -> deliveryService.reassignManager(delivery.getId(), routeId(1), target))
                .extracting("errorCode").isEqualTo(DeliveryErrorCode.DELIVERY_MANAGER_NOT_FOUND);
    }

    @Test
    @DisplayName("D-03 업체 배송 구간 담당자가 아니면 집하 담당자여도 FORBIDDEN, 업체 배송 담당자면 수정한다")
    void updateRecipientPermission() {
        UpdateRecipientCommand command = new UpdateRecipientCommand(null, "부산수산도매 2창고");

        assertThatThrownBy(() -> deliveryService.updateRecipient(manager(SUPPLIER_MANAGER), delivery.getId(), command))
                .extracting("errorCode").isEqualTo(CommonErrorCode.FORBIDDEN);
        DeliveryResponse result = deliveryService.updateRecipient(manager(RECEIVER_MANAGER), delivery.getId(), command);
        assertThat(result.delivery().recipientName()).isEqualTo("부산수산도매 2창고");
    }

    @ParameterizedTest
    @CsvSource({"false, true", "false, false", "true, false"})
    @DisplayName("D-05 현재 배송담당자가 아니거나 담당 구간이 아니면 상태를 바꾸지 않는다")
    void rejectsUnauthorizedRouteChange(boolean activeManager, boolean assigned) {
        UserContext user = new UserContext(assigned ? SUPPLIER_MANAGER : UUID.randomUUID(), "user", Role.USER, activeManager);

        assertThatThrownBy(() -> deliveryService.changeRouteStatus(user, delivery.getId(), routeId(0), MOVING))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.FORBIDDEN);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PICKUP_WAITING);
        assertThat(delivery.getRoutes()).extracting(route -> route.getStatus()).containsOnly(RouteStatus.WAITING);
    }

    @ParameterizedTest
    @CsvSource({"false, true", "false, false", "true, false"})
    @DisplayName("D-03 현재 배송담당자가 아니거나 마지막 구간 담당자가 아니면 수령 정보를 바꾸지 않는다")
    void rejectsUnauthorizedRecipientChange(boolean activeManager, boolean assigned) {
        UserContext user = new UserContext(assigned ? RECEIVER_MANAGER : UUID.randomUUID(), "user", Role.USER, activeManager);
        String address = delivery.getDeliveryAddress();
        String name = delivery.getRecipientName();

        assertThatThrownBy(() -> deliveryService.updateRecipient(user, delivery.getId(),
                new UpdateRecipientCommand("바뀐 주소", "바뀐 수령인")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.FORBIDDEN);
        assertThat(delivery.getDeliveryAddress()).isEqualTo(address);
        assertThat(delivery.getRecipientName()).isEqualTo(name);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PICKUP_WAITING);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("D-05 MASTER는 배송담당자 지정과 구간 소유에 관계없이 상태를 바꾼다")
    void masterChangesRouteRegardlessOfManagerFlag(boolean activeManager) {
        UserContext master = new UserContext(UUID.randomUUID(), "master", Role.MASTER, activeManager);

        deliveryService.changeRouteStatus(master, delivery.getId(), routeId(0), MOVING);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PICKING_UP);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("D-03 MASTER는 배송담당자 지정과 구간 소유에 관계없이 수령 정보를 바꾼다")
    void masterChangesRecipientRegardlessOfManagerFlag(boolean activeManager) {
        UserContext master = new UserContext(UUID.randomUUID(), "master", Role.MASTER, activeManager);

        deliveryService.updateRecipient(master, delivery.getId(), new UpdateRecipientCommand("바뀐 주소", "바뀐 수령인"));

        assertThat(delivery.getDeliveryAddress()).isEqualTo("바뀐 주소");
        assertThat(delivery.getRecipientName()).isEqualTo("바뀐 수령인");
    }

    private UUID routeId(int index) {
        return delivery.getRoutes().get(index).getId();
    }

    private static UserContext manager(UUID userId) {
        return new UserContext(userId, "manager", Role.USER, true);
    }
}
