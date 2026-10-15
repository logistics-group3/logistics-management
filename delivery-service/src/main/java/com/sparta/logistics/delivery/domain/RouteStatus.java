package com.sparta.logistics.delivery.domain;

/** 배송 구간 상태(SA 2.5). */
public enum RouteStatus {
    WAITING,
    MOVING,
    ARRIVED,
    CANCELED
}
