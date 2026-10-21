package com.sparta.logistics.delivery.application.dto;

import com.sparta.logistics.delivery.domain.DeliveryStatus;
import java.util.UUID;

/**
 * 내부 API I-17 결과. 주문 생성 시도(orderId)의 배송 결과를 deliveryId 없이 확인한다(이슈 #14).
 *
 * <p>상태별 의미(order가 복구 판단에 쓴다):
 * <ul>
 *   <li>ACTIVE: 배송이 있다. deliveryId·status가 채워진다.</li>
 *   <li>CLOSED: 시도가 종료됐다(I-13이 종료 표시를 남김). 이후 I-09는 배송을 만들지 않으므로 활성 배송이 생기지 않는다고 단정할 수 있다.</li>
 *   <li>NONE: 배송도 종료 표시도 없다. 원래 I-09가 아직 실행 중이거나 도착하지 않았을 수 있으므로 종료로 단정할 수 없다.</li>
 * </ul>
 */
public record DeliveryOrderResult(UUID orderId, State state, UUID deliveryId, DeliveryStatus status) {

    public enum State {
        ACTIVE, CLOSED, NONE
    }

    public static DeliveryOrderResult active(DeliveryStatusResult delivery) {
        return new DeliveryOrderResult(delivery.orderId(), State.ACTIVE, delivery.deliveryId(), delivery.status());
    }

    public static DeliveryOrderResult closed(UUID orderId) {
        return new DeliveryOrderResult(orderId, State.CLOSED, null, null);
    }

    public static DeliveryOrderResult none(UUID orderId) {
        return new DeliveryOrderResult(orderId, State.NONE, null, null);
    }
}
