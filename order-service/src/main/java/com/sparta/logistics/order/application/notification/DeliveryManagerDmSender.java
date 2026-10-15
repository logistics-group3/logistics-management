package com.sparta.logistics.order.application.notification;

import com.sparta.logistics.order.application.event.OrderCreatedEvent;

public interface DeliveryManagerDmSender {
    void send(OrderCreatedEvent event);
}
