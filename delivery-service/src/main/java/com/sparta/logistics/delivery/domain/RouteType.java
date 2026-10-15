package com.sparta.logistics.delivery.domain;

/** 배송 구간 유형(SA 5.6, delivery ADR 0003). */
public enum RouteType {
    /** 집하: 공급업체 → 공급업체 허브 */
    PICKUP,
    /** 허브 이동: 공급업체 허브 → 요청업체 허브. 두 업체가 같은 허브 소속이면 만들지 않는다 */
    HUB_TRANSFER,
    /** 업체 배송: 요청업체 허브 → 요청업체 */
    LAST_MILE
}
