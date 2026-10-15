package com.sparta.logistics.order.application;

import java.util.UUID;

public interface DeliveryReader {
    /** I-10. 주문·배송 ID가 일치하는 정상 응답에서 배송 완료 여부를 확인한다. */
    boolean isDelivered(UUID orderId, UUID deliveryId);
}
