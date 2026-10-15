package com.sparta.logistics.order.application;

import com.sparta.logistics.order.application.event.OrderCreatedEvent;

public interface OrderNotificationSender {

    /** O-01 주문 생성 알림을 비동기로 보낸다. 전달 실패는 주문 성공을 바꾸지 않는다. */
    void send(OrderCreatedEvent event);
}
