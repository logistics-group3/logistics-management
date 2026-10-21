package com.sparta.logistics.delivery.domain;

import java.util.List;
import java.util.Set;

/** 배송 상태(SA 2.5). 구간 상태가 바뀔 때 따라 바뀐다. */
public enum DeliveryStatus {
    PICKUP_WAITING,
    PICKING_UP,
    HUB_WAITING,
    HUB_MOVING,
    DEST_HUB_ARRIVED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELED;

    /** 배송지·수령인을 고칠 수 있는 상태. 업체 배송 출발 전까지다(SA 6.8 D-03). */
    private static final Set<DeliveryStatus> RECIPIENT_EDITABLE =
            Set.of(PICKUP_WAITING, PICKING_UP, HUB_WAITING, HUB_MOVING, DEST_HUB_ARRIVED);

    boolean isRecipientEditable() {
        return RECIPIENT_EDITABLE.contains(this);
    }

    /**
     * 구간 상태로 배송 상태를 계산한다. routes는 순번 순서다.
     * Note: 배송 상태를 따로 바꾸지 않고 구간 상태에서 매번 계산하면 두 값이 어긋날 수 없다.
     * 마지막으로 진행된 구간이 MOVING이면 그 유형의 이동 상태, ARRIVED면 다음 구간 유형으로 대기 상태를 정한다.
     * 같은 허브(2구간)에서 집하 ARRIVED 다음이 업체 배송이라 DEST_HUB_ARRIVED가 되는 것도 이 규칙 하나로 처리된다.
     */
    static DeliveryStatus from(List<DeliveryRoute> routes) {
        if (routes.stream().allMatch(route -> route.getStatus() == RouteStatus.CANCELED)) {
            return CANCELED;
        }
        int current = -1;
        for (int i = 0; i < routes.size(); i++) {
            if (routes.get(i).getStatus() != RouteStatus.WAITING) {
                current = i;
            }
        }
        if (current < 0) {
            return PICKUP_WAITING;
        }
        DeliveryRoute route = routes.get(current);
        if (route.getStatus() == RouteStatus.MOVING) {
            return route.getRouteType().movingStatus();
        }
        if (current == routes.size() - 1) {
            return DELIVERED;
        }
        return routes.get(current + 1).getRouteType() == RouteType.HUB_TRANSFER ? HUB_WAITING : DEST_HUB_ARRIVED;
    }
}
