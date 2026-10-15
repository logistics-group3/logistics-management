package com.sparta.logistics.delivery.domain;

/** 배송 상태(SA 2.5). 구간 상태가 바뀔 때 따라 바뀐다. */
public enum DeliveryStatus {
    PICKUP_WAITING,
    PICKING_UP,
    HUB_WAITING,
    HUB_MOVING,
    DEST_HUB_ARRIVED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELED
}
