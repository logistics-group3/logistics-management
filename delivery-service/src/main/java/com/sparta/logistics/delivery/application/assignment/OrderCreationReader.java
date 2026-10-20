package com.sparta.logistics.delivery.application.assignment;

import java.util.UUID;

/** order의 주문 생성 결과(I-21). 확정을 받지 못한 배송이 커밋된 주문의 것인지 확인한다(결정 로그 29). */
public interface OrderCreationReader {

    OrderCreationCheck getCreationResult(UUID orderId);
}
