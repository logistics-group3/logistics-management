package com.sparta.logistics.delivery.domain;

import com.sparta.logistics.common.entity.BaseEntity;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * 배송(SA 5.6 p_deliveries). 주문 1건 = 배송 1건이고, 구간(DeliveryRoute)을 함께 관리하는 애그리거트 루트다.
 * Note: 구간 상태는 이 애그리거트를 통해서만 바꾼다. 그래야 구간 상태와 배송 상태가 한 트랜잭션 안에서 함께 바뀌어 어긋나지 않는다.
 */
@Getter
@Entity
@Table(name = "p_deliveries")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Delivery extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "delivery_id")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DeliveryStatus status;

    @Column(name = "pickup_address", nullable = false)
    private String pickupAddress;

    @Column(name = "delivery_address", nullable = false)
    private String deliveryAddress;

    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;

    @Column(name = "recipient_slack_id", length = 50)
    private String recipientSlackId;

    /** order-service p_orders.order_id 논리 참조. I-09·I-13 멱등키 */
    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    /** 공급업체. company-service 논리 참조 */
    @Column(name = "supplier_company_id", nullable = false)
    private UUID supplierCompanyId;

    /** 요청업체. company-service 논리 참조 */
    @Column(name = "receiver_company_id", nullable = false)
    private UUID receiverCompanyId;

    /** 출발 허브 = 공급업체 관리 허브(생성 시점 값). hub-service 논리 참조 */
    @Column(name = "departure_hub_id", nullable = false)
    private UUID departureHubId;

    /** 목적지 허브 = 요청업체 관리 허브(생성 시점 값). hub-service 논리 참조 */
    @Column(name = "destination_hub_id", nullable = false)
    private UUID destinationHubId;

    /**
     * Note: cascade는 PERSIST만 둔다. 구간은 배송과 함께 처음 저장될 때만 따라 저장되면 되고, 이후 변경은 영속 상태의
     * 변경 감지로 반영된다. 물리 삭제를 하지 않으므로 REMOVE와 orphanRemoval도 두지 않는다(논리 삭제는 softDelete가 구간까지 처리).
     */
    @OneToMany(mappedBy = "delivery", cascade = CascadeType.PERSIST)
    @OrderBy("sequence ASC")
    private List<DeliveryRoute> routes = new ArrayList<>();

    /** 주문 확정 시각(I-19). NULL이면 주문이 아직 커밋되지 않았을 수 있어 배정하지 않는다(결정 로그 29). */
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    /** 수동 지정 요청 시각. 값이 있으면 자동 배정이 이 배송을 건너뛰고 MASTER가 D-04로 지정한다(결정 로그 29). */
    @Column(name = "manual_assignment_requested_at")
    private LocalDateTime manualAssignmentRequestedAt;

    /**
     * I-09 배송 생성. 같은 허브면 집하·업체 배송 2구간, 다르면 집하·허브 이동·업체 배송 3구간을 만든다(ADR 0003).
     * 구간은 담당자 없이 미확정으로 만든다. 주문이 확정되면(confirm) 허브별 배정 작업이 assignPendingRoutes로 채운다(결정 로그 27·29).
     *
     * @param hubTransferExpected 허브 이동 구간의 예상 거리·시간(I-03). 같은 허브면 쓰지 않는다
     */
    public static Delivery create(UUID orderId, UUID supplierCompanyId, UUID receiverCompanyId,
                                  UUID departureHubId, UUID destinationHubId,
                                  String pickupAddress, String deliveryAddress,
                                  String recipientName, String recipientSlackId,
                                  RouteInfo hubTransferExpected) {
        Delivery delivery = new Delivery();
        delivery.orderId = orderId;
        delivery.supplierCompanyId = supplierCompanyId;
        delivery.receiverCompanyId = receiverCompanyId;
        delivery.departureHubId = departureHubId;
        delivery.destinationHubId = destinationHubId;
        delivery.pickupAddress = pickupAddress;
        delivery.deliveryAddress = deliveryAddress;
        delivery.recipientName = recipientName;
        delivery.recipientSlackId = recipientSlackId;

        delivery.addRoute(RouteType.PICKUP, null, departureHubId, null);
        if (!delivery.isSameHub()) {
            delivery.addRoute(RouteType.HUB_TRANSFER, departureHubId, destinationHubId, hubTransferExpected);
        }
        delivery.addRoute(RouteType.LAST_MILE, destinationHubId, null, null);
        delivery.status = DeliveryStatus.PICKUP_WAITING;
        return delivery;
    }

    /** 공급업체와 요청업체가 같은 허브 소속이면 허브 이동 구간이 없다. */
    public boolean isSameHub() {
        return departureHubId.equals(destinationHubId);
    }

    public List<DeliveryRoute> getRoutes() {
        return Collections.unmodifiableList(routes);
    }

    public DeliveryRoute getRoute(UUID routeId) {
        return routes.stream()
                .filter(route -> routeId.equals(route.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_ROUTE_NOT_FOUND));
    }

    /** 업체 배송 구간. 배송지·수령인 수정 권한(D-03)을 판단할 때 쓴다. */
    public DeliveryRoute getLastMileRoute() {
        return routes.get(routes.size() - 1);
    }

    /** D-05 구간 상태 변경. 목표 상태는 MOVING, ARRIVED만 있다. 그 밖의 값은 허용되지 않는 전이다. */
    public void changeRouteStatus(UUID routeId, RouteStatus target, RouteInfo actual) {
        switch (target) {
            case MOVING -> startRoute(routeId);
            case ARRIVED -> arriveRoute(routeId, actual);
            default -> throw new BusinessException(CommonErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    /** 구간 출발. 앞 구간이 ARRIVED여야 한다(SA 2.5). */
    public void startRoute(UUID routeId) {
        DeliveryRoute route = getRoute(routeId);
        if (route.getSequence() > 1 && routes.get(route.getSequence() - 2).getStatus() != RouteStatus.ARRIVED) {
            throw new BusinessException(CommonErrorCode.INVALID_STATUS_TRANSITION);
        }
        route.start();
        refreshStatus();
    }

    /** 구간 도착. 허브 이동은 실제 거리·시간이 필수다. */
    public void arriveRoute(UUID routeId, RouteInfo actual) {
        getRoute(routeId).arrive(actual);
        refreshStatus();
    }

    /** I-11 배송 취소. 집하 시작 전(PICKUP_WAITING)에만 된다. 이미 취소됐으면 그대로 둔다(SA 7.3 멱등). */
    public void cancel() {
        if (status == DeliveryStatus.CANCELED) {
            return;
        }
        if (status != DeliveryStatus.PICKUP_WAITING) {
            throw new BusinessException(CommonErrorCode.INVALID_STATUS_TRANSITION);
        }
        routes.forEach(DeliveryRoute::cancel);
        refreshStatus();
    }

    /**
     * I-12 취소 원복(보상). 취소는 PICKUP_WAITING에서만 일어나므로 구간을 모두 WAITING으로 되돌리면 취소 전 상태다.
     * 취소 상태가 아니면(이미 원복함) 아무것도 하지 않는다(SA 7.3 멱등).
     */
    public void revertCancel() {
        if (status != DeliveryStatus.CANCELED) {
            return;
        }
        routes.forEach(DeliveryRoute::revertCancel);
        refreshStatus();
    }

    /** D-03 배송지·수령인 수정. 보낸 값만 바꾸고, 업체 배송 출발(OUT_FOR_DELIVERY) 뒤에는 409. */
    public void updateRecipient(String deliveryAddress, String recipientName) {
        if (!status.isRecipientEditable()) {
            throw new BusinessException(CommonErrorCode.INVALID_STATUS_TRANSITION);
        }
        if (deliveryAddress != null) {
            this.deliveryAddress = deliveryAddress;
        }
        if (recipientName != null) {
            this.recipientName = recipientName;
        }
    }

    /**
     * D-04 재배정. 대상의 소속 허브가 구간 담당 허브가 아니면 400, 도착·취소된 구간이면 409.
     *
     * @return 이번 지정으로 확정된 배송의 배정 대기 구간이 모두 채워졌으면 true(배정 완료 알림 대상, 결정 로그 29)
     */
    public boolean reassignRouteManager(UUID routeId, DeliveryManager manager) {
        DeliveryRoute route = getRoute(routeId);
        if (!route.getManagingHubId().equals(manager.hubId())) {
            throw new BusinessException(DeliveryErrorCode.MANAGER_HUB_MISMATCH);
        }
        boolean wasAwaiting = !hasNoRouteAwaitingManager();
        route.changeManager(manager.userId());
        return isConfirmed() && wasAwaiting && hasNoRouteAwaitingManager();
    }

    /**
     * I-19 주문 확정. 이때부터 구간 담당자를 배정한다(결정 로그 29). 이미 확정됐으면 그대로 둔다(멱등).
     *
     * @return 이번 호출로 확정됐으면 true
     */
    public boolean confirm() {
        if (confirmedAt != null) {
            return false;
        }
        confirmedAt = LocalDateTime.now();
        return true;
    }

    public boolean isConfirmed() {
        return confirmedAt != null;
    }

    /**
     * 후보가 없는 상태가 기준 시간을 넘은 배송을 수동 지정으로 넘긴다. 이후 자동 배정은 이 배송을 건너뛴다(결정 로그 29).
     * 이미 넘겼거나 배정 대기 구간이 없으면 그대로 둔다.
     *
     * @return 이번 호출로 넘겼으면 true(MASTER 알림 대상)
     */
    public boolean requestManualAssignment() {
        if (manualAssignmentRequestedAt != null || hasNoRouteAwaitingManager()) {
            return false;
        }
        manualAssignmentRequestedAt = LocalDateTime.now();
        return true;
    }

    /** 배정 대기 구간의 담당 허브. 확정 직후 깨울 배정 작업을 고른다. */
    public Set<UUID> hubsAwaitingManager() {
        return routes.stream()
                .filter(DeliveryRoute::isAwaitingManager)
                .map(DeliveryRoute::getManagingHubId)
                .collect(Collectors.toSet());
    }

    /** I-13 배송 삭제. 구간도 함께 논리 삭제한다(SA 7.5). 이미 삭제된 행은 그대로 둔다. */
    @Override
    public void softDelete(String username) {
        super.softDelete(username);
        routes.forEach(route -> route.softDelete(username));
    }

    /** I-18 생성 실패 정리. 출발과 같은 배송 행 잠금 안에서 검사한 뒤에만 삭제한다. */
    public void closeCreation(UUID expectedDeliveryId, String username) {
        if ((expectedDeliveryId != null && !expectedDeliveryId.equals(id))
                || status != DeliveryStatus.PICKUP_WAITING
                || routes.stream().anyMatch(route -> route.getStatus() != RouteStatus.WAITING)) {
            throw new BusinessException(CommonErrorCode.INVALID_STATUS_TRANSITION);
        }
        softDelete(username);
    }

    /**
     * 자동 배정(SA 6.3). 이 허브가 담당하는 배정 대기 구간에 순번대로 담당자를 넣는다. 고를 때마다 assigner가 고른 사람의 건수를
     * 올리므로 같은 배송의 집하·허브 이동이 한 사람에게 몰리지 않는다. 이미 배정된 구간은 건너뛴다(멱등).
     *
     * @return 이번에 담당자를 넣은 구간 수
     */
    public int assignPendingRoutes(UUID managingHubId, ManagerAssigner assigner) {
        int assigned = 0;
        for (DeliveryRoute route : routes) {
            if (route.getManagingHubId().equals(managingHubId) && route.isAwaitingManager()
                    && route.assignManager(assigner.assign(managingHubId))) {
                assigned++;
            }
        }
        return assigned;
    }

    /** 배정 대기 구간이 하나도 없다. 취소된 구간은 배정하지 않으므로 대기로 세지 않는다. */
    public boolean hasNoRouteAwaitingManager() {
        return routes.stream().noneMatch(DeliveryRoute::isAwaitingManager);
    }

    private void addRoute(RouteType type, UUID departureHub, UUID arrivalHub, RouteInfo expected) {
        routes.add(new DeliveryRoute(this, routes.size() + 1, type, departureHub, arrivalHub, expected));
    }

    private void refreshStatus() {
        this.status = DeliveryStatus.from(routes);
    }
}
