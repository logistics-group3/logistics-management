package com.sparta.logistics.order.domain;

/** 주문 상태(SA 5.5). 배송 진행 상태는 delivery-service가 관리한다. */
public enum OrderStatus {
    ORDERED,
    CANCELED
}
