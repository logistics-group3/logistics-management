package com.sparta.logistics.order.application.notification;

import java.util.UUID;

/** I-20 배정 결과 알림 요청(delivery 결정 로그 29). 다른 스레드로 넘기는 값이라 요청 컨텍스트를 담지 않는다. */
public record AssignmentNotification(UUID orderId, UUID deliveryId, Type type) {

    public enum Type {
        /** 배송의 모든 구간에 담당자가 정해졌다. 담당자에게 DM을 보낸다 */
        ASSIGNED,
        /** 후보가 없어 수동 지정으로 넘겼다. MASTER에게 지정을 요청한다 */
        MANUAL_REQUIRED
    }
}
