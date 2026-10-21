package com.sparta.logistics.delivery.domain;

import java.util.UUID;

/** 배송 구간 유형(SA 5.6, delivery ADR 0003). */
public enum RouteType {
    /** 집하: 공급업체 → 공급업체 허브 */
    PICKUP(DeliveryStatus.PICKING_UP),
    /** 허브 이동: 공급업체 허브 → 요청업체 허브. 두 업체가 같은 허브 소속이면 만들지 않는다 */
    HUB_TRANSFER(DeliveryStatus.HUB_MOVING),
    /** 업체 배송: 요청업체 허브 → 요청업체 */
    LAST_MILE(DeliveryStatus.OUT_FOR_DELIVERY);

    /** 이 유형의 구간이 MOVING일 때의 배송 상태(SA 2.5) */
    private final DeliveryStatus movingStatus;

    RouteType(DeliveryStatus movingStatus) {
        this.movingStatus = movingStatus;
    }

    DeliveryStatus movingStatus() {
        return movingStatus;
    }

    /**
     * 구간 담당 허브: 구간 출발지를 관리하는 허브다(SA 1.5 #13, 5.6). 집하는 업체에서 출발하므로 도착 허브(공급업체 허브),
     * 허브 이동·업체 배송은 출발 허브다.
     */
    public UUID managingHubId(UUID departureHubId, UUID arrivalHubId) {
        return this == PICKUP ? arrivalHubId : departureHubId;
    }
}
