package com.sparta.logistics.delivery.application;

import static com.sparta.logistics.delivery.DeliveryFixture.RECEIVER_HUB;
import static com.sparta.logistics.delivery.DeliveryFixture.RECEIVER_MANAGER;
import static com.sparta.logistics.delivery.DeliveryFixture.SUPPLIER_MANAGER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.delivery.DeliveryFixture;
import com.sparta.logistics.delivery.application.dto.ChangeRouteStatusCommand;
import com.sparta.logistics.delivery.application.dto.DeliveryResponse;
import com.sparta.logistics.delivery.application.dto.UpdateRecipientCommand;
import com.sparta.logistics.delivery.domain.Delivery;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.AuditorAware;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    private static final ChangeRouteStatusCommand MOVING = new ChangeRouteStatusCommand(RouteStatus.MOVING, null, null);

    @Mock
    private DeliveryRepository deliveryRepository;
    @Mock
    private DeliveryManagerReader deliveryManagerReader;
    @Mock
    private AuditorAware<String> auditorAware;
    @InjectMocks
    private DeliveryService deliveryService;

    private Delivery delivery;

    @BeforeEach
    void setUp() {
        delivery = DeliveryFixture.threeRoutes();
        given(deliveryRepository.findById(delivery.getId())).willReturn(Optional.of(delivery));
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

    private UUID routeId(int index) {
        return delivery.getRoutes().get(index).getId();
    }

    private static UserContext manager(UUID userId) {
        return new UserContext(userId, "manager", Role.USER, true);
    }
}
