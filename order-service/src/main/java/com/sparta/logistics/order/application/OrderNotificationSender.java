package com.sparta.logistics.order.application;

import com.sparta.logistics.order.application.event.OrderCreatedEvent;
import java.util.UUID;

public interface OrderNotificationSender {

    /** O-01 주문 생성 알림을 비동기로 보낸다. 전달 실패는 주문 성공을 바꾸지 않는다. */
    void send(OrderCreatedEvent event);

    /** 후보가 없어 수동 지정으로 넘긴 배송을 MASTER에게 알린다(delivery 결정 로그 29). 전달 실패는 기록만 한다. */
    void sendManualAssignmentRequired(UUID orderId, UUID deliveryId);
}
