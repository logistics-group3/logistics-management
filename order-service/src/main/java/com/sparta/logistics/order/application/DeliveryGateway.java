package com.sparta.logistics.order.application;

import com.sparta.logistics.order.application.dto.CreateDeliveryCommand;
import java.util.UUID;

/** delivery 내부 API 계약. 응답 오류와 통신 오류는 공통 Feign 규칙대로 전달한다. */
public interface DeliveryGateway {

    /** I-09. orderId로 멱등하며 생성한 배송 ID를 반환한다. */
    UUID create(CreateDeliveryCommand command);

    /** I-13 한 번 호출. 대상이 없어도 성공이다. 보상 재시도는 OrderService가 맡아 중첩 재시도를 막는다. */
    void deleteByOrderId(UUID orderId);

    /** I-11. 집하 시작 전인지 판단하고 배송·구간을 취소한다. 자동 재시도하지 않는다. */
    void cancel(UUID deliveryId);

    /** I-12. 확인된 배송 취소를 원복한다. 응답 없는 통신 실패만 최대 3회 시도한다. */
    void revertCancellation(UUID deliveryId);
}
