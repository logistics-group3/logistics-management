package com.sparta.logistics.order.application.event;

import com.sparta.logistics.order.domain.Order;
import java.util.UUID;

/** O-01 생성 시점의 알림 데이터. 다른 스레드로 JPA Entity나 요청 컨텍스트를 넘기지 않는다. */
public record OrderCreatedEvent(UUID orderId, String supplierCompanyName, String receiverCompanyName,
                                String productName, int quantity, String requestNote) {

    public static OrderCreatedEvent from(Order order, String supplierCompanyName, String receiverCompanyName) {
        return new OrderCreatedEvent(order.getId(), supplierCompanyName, receiverCompanyName,
                order.getProductName(), order.getQuantity(), order.getRequestNote());
    }
}
