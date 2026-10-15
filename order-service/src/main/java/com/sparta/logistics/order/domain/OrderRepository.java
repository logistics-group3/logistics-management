package com.sparta.logistics.order.domain;

import java.util.Optional;
import java.util.UUID;

/** 주문 application에서 사용하는 저장소 계약. Spring Data 구현은 infrastructure에 둔다. */
public interface OrderRepository {

    /** SQL을 즉시 실행하고 저장된 Entity를 반환한다. flush는 트랜잭션 커밋과 다르다. */
    Order saveAndFlush(Order order);

    Optional<Order> findById(UUID id);

    Optional<Order> findByIdForUpdate(UUID id);

    void flush();
}
