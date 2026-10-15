package com.sparta.logistics.delivery.domain;

import jakarta.persistence.Embeddable;
import java.math.BigDecimal;

/**
 * 구간의 거리와 소요시간. 예상값과 실제값에 같은 타입을 쓴다.
 * Note: 거리와 시간은 항상 함께 다루는 값이라 값 객체(@Embeddable)로 묶는다. 컬럼 이름은 구간 Entity가 @AttributeOverride로 정한다.
 */
@Embeddable
public record RouteInfo(BigDecimal distanceKm, Integer durationMinutes) {

    boolean hasBoth() {
        return distanceKm != null && durationMinutes != null;
    }

    boolean isEmpty() {
        return distanceKm == null && durationMinutes == null;
    }
}
