package com.sparta.logistics.delivery.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * 배송 명령(생성·변경·삭제)용 저장소. Entity를 읽고 저장하는 일만 한다.
 * 조회 화면용 데이터는 application/DeliveryQueryRepository가 DTO로 바로 읽는다(결정 로그 17).
 */
public interface DeliveryRepository {

    Delivery save(Delivery delivery);

    /** 배송과 구간을 저장하고 바로 INSERT한다. order_id 유니크 위반을 호출한 곳에서 잡기 위해 쓴다. */
    Delivery saveAndFlush(Delivery delivery);

    Optional<Delivery> findById(UUID id);

    Optional<Delivery> findByOrderId(UUID orderId);
}
