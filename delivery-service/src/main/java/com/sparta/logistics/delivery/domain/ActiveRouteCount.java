package com.sparta.logistics.delivery.domain;

import java.util.UUID;

/** 배송담당자의 담당 건수: 맡은 구간 중 WAITING·MOVING 수(SA 6.3). 구간이 없는 사람은 결과에 나오지 않는다. */
public record ActiveRouteCount(UUID managerId, Long count) {
}
