package com.sparta.logistics.order.application;

import com.sparta.logistics.order.application.dto.CreateDeliveryCommand;
import com.sparta.logistics.order.application.dto.DeliveryCreationState;
import java.util.UUID;

/** delivery 내부 API 계약. 응답 오류와 통신 오류는 공통 Feign 규칙대로 전달한다. */
public interface DeliveryGateway {

    /** I-09. orderId로 멱등하며 생성한 배송 ID를 반환한다. */
    UUID create(CreateDeliveryCommand command);

    /** I-13 한 번 호출. 대상이 없어도 성공이다. 보상 재시도는 OrderService가 맡아 중첩 재시도를 막는다. */
    void deleteByOrderId(UUID orderId);

    /**
     * I-17. orderId의 생성 시도가 종료돼 더는 배송이 생기지 않는지 확인한다. 종료(CLOSED)일 때만 true이고,
     * 배송이 있거나(ACTIVE) 결과가 아직 없으면(NONE) false다. 응답을 해석할 수 없으면 예외를 던진다.
     */
    boolean isCreationClosed(UUID orderId);

    /** I-17 전체 결과. 식별자·상태 조합을 검증한다. */
    DeliveryCreationState getCreationState(UUID orderId);

    /** I-18 생성 보상은 출발 전 배송만 종료한다. */
    void closeCreation(UUID orderId);

    /** I-18 복구 시 관찰한 배송 ID도 비교한다. */
    void closeCreation(UUID orderId, UUID expectedDeliveryId);

    /** I-11. 집하 시작 전인지 판단하고 배송·구간을 취소한다. 자동 재시도하지 않는다. */
    void cancel(UUID deliveryId);

    /** I-12. 확인된 배송 취소를 원복한다. 응답 없는 통신 실패만 최대 3회 시도한다. */
    void revertCancellation(UUID deliveryId);
}
