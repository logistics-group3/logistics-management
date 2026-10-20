package com.sparta.logistics.delivery.domain;

import static com.sparta.logistics.delivery.DeliveryFixture.RECEIVER_HUB;
import static com.sparta.logistics.delivery.DeliveryFixture.RECEIVER_MANAGER;
import static com.sparta.logistics.delivery.DeliveryFixture.SUPPLIER_HUB;
import static com.sparta.logistics.delivery.DeliveryFixture.SUPPLIER_MANAGER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.common.exception.ErrorCode;
import com.sparta.logistics.delivery.DeliveryFixture;
import java.math.BigDecimal;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 배송 상태 전이(SA 2.5, delivery.md 3-2 전이표). Spring 없이 Entity 메서드만 검증한다. */
class DeliveryTest {

    private static final RouteInfo ACTUAL = new RouteInfo(new BigDecimal("402.30"), 305);

    @Test
    @DisplayName("허브가 다르면 3구간을 만들고 집하·허브 이동은 공급업체 허브, 업체 배송은 요청업체 허브 담당자가 맡는다")
    void createThreeRoutes() {
        Delivery delivery = DeliveryFixture.threeRoutes();

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PICKUP_WAITING);
        assertThat(delivery.getRoutes())
                .extracting(DeliveryRoute::getSequence, DeliveryRoute::getRouteType, DeliveryRoute::getDepartureHubId,
                        DeliveryRoute::getArrivalHubId, DeliveryRoute::getManagerId, DeliveryRoute::getStatus)
                .containsExactly(
                        tuple(1, RouteType.PICKUP, null, SUPPLIER_HUB, SUPPLIER_MANAGER, RouteStatus.WAITING),
                        tuple(2, RouteType.HUB_TRANSFER, SUPPLIER_HUB, RECEIVER_HUB, SUPPLIER_MANAGER, RouteStatus.WAITING),
                        tuple(3, RouteType.LAST_MILE, RECEIVER_HUB, null, RECEIVER_MANAGER, RouteStatus.WAITING));
        assertThat(delivery.getRoutes().get(1).getExpected()).isEqualTo(DeliveryFixture.HUB_ROUTE);
    }

    @Test
    @DisplayName("3구간 배송은 구간 상태를 따라 PICKUP_WAITING부터 DELIVERED까지 순서대로 바뀐다")
    void threeRouteFlow() {
        Delivery delivery = DeliveryFixture.threeRoutes();
        UUID pickup = routeId(delivery, 0);
        UUID transfer = routeId(delivery, 1);
        UUID lastMile = routeId(delivery, 2);

        delivery.startRoute(pickup);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PICKING_UP);
        delivery.arriveRoute(pickup, null);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.HUB_WAITING);
        delivery.startRoute(transfer);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.HUB_MOVING);
        delivery.arriveRoute(transfer, ACTUAL);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DEST_HUB_ARRIVED);
        delivery.startRoute(lastMile);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.OUT_FOR_DELIVERY);
        delivery.arriveRoute(lastMile, null);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(delivery.getRoutes().get(1).getActual()).isEqualTo(ACTUAL);
    }

    @Test
    @DisplayName("같은 허브 2구간 배송은 집하 도착 때 바로 DEST_HUB_ARRIVED가 된다")
    void twoRouteFlow() {
        Delivery delivery = DeliveryFixture.twoRoutes();
        UUID pickup = routeId(delivery, 0);
        UUID lastMile = routeId(delivery, 1);

        assertThat(delivery.getRoutes()).extracting(DeliveryRoute::getRouteType)
                .containsExactly(RouteType.PICKUP, RouteType.LAST_MILE);
        assertThat(delivery.getRoutes()).extracting(DeliveryRoute::getManagerId)
                .containsOnly(SUPPLIER_MANAGER);

        delivery.startRoute(pickup);
        delivery.arriveRoute(pickup, null);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DEST_HUB_ARRIVED);
        delivery.startRoute(lastMile);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.OUT_FOR_DELIVERY);
        delivery.arriveRoute(lastMile, null);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    @DisplayName("앞 구간이 도착하기 전에 다음 구간을 출발시키면 INVALID_STATUS_TRANSITION")
    void cannotStartBeforePreviousArrives() {
        Delivery delivery = DeliveryFixture.threeRoutes();
        UUID pickup = routeId(delivery, 0);

        assertInvalidTransition(() -> delivery.startRoute(routeId(delivery, 1)));
        delivery.startRoute(pickup);
        assertInvalidTransition(() -> delivery.startRoute(routeId(delivery, 1)));
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PICKING_UP);
    }

    @Test
    @DisplayName("구간 상태는 한 단계씩만 바뀐다: WAITING에서 바로 도착하거나 도착한 구간을 다시 출발시킬 수 없다")
    void cannotSkipOrRepeat() {
        Delivery delivery = DeliveryFixture.threeRoutes();
        UUID pickup = routeId(delivery, 0);

        assertInvalidTransition(() -> delivery.arriveRoute(pickup, null));
        delivery.startRoute(pickup);
        assertInvalidTransition(() -> delivery.startRoute(pickup));
        delivery.arriveRoute(pickup, null);
        assertInvalidTransition(() -> delivery.startRoute(pickup));
        assertInvalidTransition(() -> delivery.changeRouteStatus(pickup, RouteStatus.WAITING, null));
    }

    @Test
    @DisplayName("허브 이동 구간은 실제 거리·시간 없이 도착 처리하면 VALIDATION_ERROR이고 상태가 그대로다")
    void hubTransferArrivalRequiresActual() {
        Delivery delivery = DeliveryFixture.threeRoutes();
        UUID pickup = routeId(delivery, 0);
        UUID transfer = routeId(delivery, 1);
        delivery.startRoute(pickup);
        delivery.arriveRoute(pickup, null);
        delivery.startRoute(transfer);

        assertError(() -> delivery.arriveRoute(transfer, new RouteInfo(new BigDecimal("402.30"), null)),
                CommonErrorCode.VALIDATION_ERROR);
        assertThat(delivery.getRoute(transfer).getStatus()).isEqualTo(RouteStatus.MOVING);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.HUB_MOVING);
    }

    @Test
    @DisplayName("집하가 시작된 배송은 취소할 수 없다")
    void cancelAfterPickupStarted() {
        Delivery delivery = DeliveryFixture.threeRoutes();
        delivery.startRoute(routeId(delivery, 0));

        assertInvalidTransition(delivery::cancel);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PICKING_UP);
    }

    @Test
    @DisplayName("취소하면 구간이 모두 CANCELED가 되고, 다시 취소해도 그대로이며 취소된 구간은 출발할 수 없다")
    void cancelIsIdempotent() {
        Delivery delivery = DeliveryFixture.threeRoutes();

        delivery.cancel();
        delivery.cancel();

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.CANCELED);
        assertThat(delivery.getRoutes()).extracting(DeliveryRoute::getStatus).containsOnly(RouteStatus.CANCELED);
        assertInvalidTransition(() -> delivery.startRoute(routeId(delivery, 0)));
    }

    @Test
    @DisplayName("취소 원복은 PICKUP_WAITING과 구간 전부 WAITING으로 되돌리고, 원복 후 재요청은 아무것도 바꾸지 않는다")
    void revertCancel() {
        Delivery delivery = DeliveryFixture.threeRoutes();
        delivery.cancel();

        delivery.revertCancel();
        delivery.revertCancel();

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PICKUP_WAITING);
        assertThat(delivery.getRoutes()).extracting(DeliveryRoute::getStatus).containsOnly(RouteStatus.WAITING);
        delivery.startRoute(routeId(delivery, 0));
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PICKING_UP);
    }

    @Test
    @DisplayName("업체 배송이 출발한 뒤에는 배송지·수령인을 고칠 수 없다")
    void updateRecipientBeforeOutForDelivery() {
        Delivery delivery = DeliveryFixture.twoRoutes();
        delivery.updateRecipient(null, "부산수산도매 2창고");
        delivery.startRoute(routeId(delivery, 0));
        delivery.arriveRoute(routeId(delivery, 0), null);
        delivery.startRoute(routeId(delivery, 1));

        assertInvalidTransition(() -> delivery.updateRecipient("부산광역시 중구 자갈치해안로 60", null));
        assertThat(delivery.getRecipientName()).isEqualTo("부산수산도매 2창고");
        assertThat(delivery.getDeliveryAddress()).isEqualTo("부산광역시 중구 자갈치해안로 52");
    }

    @Test
    @DisplayName("재배정 대상의 소속 허브가 구간 담당 허브가 아니면 MANAGER_HUB_MISMATCH, 도착한 구간이면 INVALID_STATUS_TRANSITION")
    void reassignRouteManager() {
        Delivery delivery = DeliveryFixture.threeRoutes();
        UUID pickup = routeId(delivery, 0);
        UUID transfer = routeId(delivery, 1);
        UUID newManager = UUID.randomUUID();

        assertError(() -> delivery.reassignRouteManager(transfer, new DeliveryManager(newManager, RECEIVER_HUB)),
                DeliveryErrorCode.MANAGER_HUB_MISMATCH);
        delivery.reassignRouteManager(transfer, new DeliveryManager(newManager, SUPPLIER_HUB));
        assertThat(delivery.getRoute(transfer).getManagerId()).isEqualTo(newManager);

        delivery.startRoute(pickup);
        delivery.arriveRoute(pickup, null);
        assertInvalidTransition(() -> delivery.reassignRouteManager(pickup, new DeliveryManager(newManager, SUPPLIER_HUB)));
    }

    @Test
    @DisplayName("다른 배송의 구간 ID면 DELIVERY_ROUTE_NOT_FOUND")
    void routeOfAnotherDelivery() {
        Delivery delivery = DeliveryFixture.threeRoutes();
        UUID otherRoute = routeId(DeliveryFixture.threeRoutes(), 0);

        assertError(() -> delivery.startRoute(otherRoute), DeliveryErrorCode.DELIVERY_ROUTE_NOT_FOUND);
    }

    @Test
    @DisplayName("배송을 논리 삭제하면 구간도 함께 삭제된다")
    void softDeleteIncludesRoutes() {
        Delivery delivery = DeliveryFixture.threeRoutes();

        delivery.softDelete("master01");

        assertThat(delivery.isDeleted()).isTrue();
        assertThat(delivery.getRoutes()).allMatch(DeliveryRoute::isDeleted)
                .extracting(DeliveryRoute::getDeletedBy).containsOnly("master01");
    }

    private static UUID routeId(Delivery delivery, int index) {
        return delivery.getRoutes().get(index).getId();
    }

    private static void assertInvalidTransition(ThrowingCallable call) {
        assertError(call, CommonErrorCode.INVALID_STATUS_TRANSITION);
    }

    private static void assertError(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("배송 생성 직후 구간은 담당자가 없는 배정 대기이고, 허브별 배정은 그 허브가 담당하는 대기 구간만 채운다")
    void assignPendingRoutesByHub() {
        Delivery delivery = DeliveryFixture.unassigned(SUPPLIER_HUB, RECEIVER_HUB);
        assertThat(delivery.getRoutes()).allMatch(DeliveryRoute::isAwaitingManager);

        int assigned = delivery.assignPendingRoutes(SUPPLIER_HUB, DeliveryFixture.assigner());

        // 집하·허브 이동은 공급업체 허브 담당, 업체 배송은 요청업체 허브 담당이라 아직 대기
        assertThat(assigned).isEqualTo(2);
        assertThat(delivery.getRoutes()).extracting(DeliveryRoute::getManagerId)
                .containsExactly(SUPPLIER_MANAGER, SUPPLIER_MANAGER, null);
        assertThat(delivery.getRoutes().get(0).getAssignedAt()).isNotNull();
        assertThat(delivery.hasNoRouteAwaitingManager()).isFalse();
    }

    @Test
    @DisplayName("이미 배정된 구간은 다시 배정해도 그대로다(배정 작업이 여러 번 불려도 결과가 같다)")
    void assignIsIdempotent() {
        Delivery delivery = DeliveryFixture.threeRoutes();

        int assigned = delivery.assignPendingRoutes(SUPPLIER_HUB, DeliveryFixture.assigner())
                + delivery.assignPendingRoutes(RECEIVER_HUB, DeliveryFixture.assigner());

        assertThat(assigned).isZero();
        assertThat(delivery.hasNoRouteAwaitingManager()).isTrue();
        assertThat(delivery.getRoutes()).extracting(DeliveryRoute::getManagerId)
                .containsExactly(SUPPLIER_MANAGER, SUPPLIER_MANAGER, RECEIVER_MANAGER);
    }

    @Test
    @DisplayName("담당자가 없는 구간은 출발할 수 없고(INVALID_STATUS_TRANSITION), MASTER가 재배정하면 출발한다")
    void cannotStartUnassignedRoute() {
        Delivery delivery = DeliveryFixture.unassigned(SUPPLIER_HUB, SUPPLIER_HUB);
        UUID pickup = UUID.randomUUID();
        ReflectionTestUtils.setField(delivery.getRoutes().get(0), "id", pickup);

        assertThatThrownBy(() -> delivery.startRoute(pickup))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_STATUS_TRANSITION);

        delivery.reassignRouteManager(pickup, new DeliveryManager(SUPPLIER_MANAGER, SUPPLIER_HUB));
        delivery.startRoute(pickup);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PICKING_UP);
        assertThat(delivery.getRoutes().get(0).getAssignedAt()).isNotNull();
    }

    @Test
    @DisplayName("취소된 구간은 배정 대기가 아니라서 배정하지 않는다")
    void canceledRouteIsNotAssigned() {
        Delivery delivery = DeliveryFixture.unassigned(SUPPLIER_HUB, SUPPLIER_HUB);
        delivery.cancel();

        assertThat(delivery.assignPendingRoutes(SUPPLIER_HUB, DeliveryFixture.assigner())).isZero();
        assertThat(delivery.hasNoRouteAwaitingManager()).isTrue();
    }
}
