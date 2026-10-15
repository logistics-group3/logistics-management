package com.sparta.logistics.order.application;

import com.sparta.logistics.order.application.dto.CreateDeliveryCommand;
import java.util.UUID;

/** delivery 내부 API 계약. 응답 오류와 통신 오류는 공통 Feign 규칙대로 전달한다. */
public interface DeliveryGateway {

    /** I-09. orderId로 멱등하며 생성한 배송 ID를 반환한다. */
    UUID create(CreateDeliveryCommand command);

    /** I-13 한 번 호출. 대상이 없어도 성공이다. 보상 재시도는 OrderService가 맡아 중첩 재시도를 막는다. */
    void deleteByOrderId(UUID orderId);
}
