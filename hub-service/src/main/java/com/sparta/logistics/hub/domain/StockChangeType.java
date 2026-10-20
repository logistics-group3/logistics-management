package com.sparta.logistics.hub.domain;

/** 재고 변경 유형(SA 5.3). 입고는 주문과 무관하고, 차감·복원은 주문 ID로 멱등을 보장한다. */
public enum StockChangeType {
    INBOUND,
    DECREASE,
    RESTORE
}
