package com.sparta.logistics.order.domain;

import java.util.Optional;
import java.util.UUID;

public interface OrderCreationRepository {

    OrderCreation saveAndFlush(OrderCreation creation);

    Optional<OrderCreation> findByOrderIdForUpdate(UUID orderId);

    /** 진행 조회용. 논리 삭제된 기록은 없는 것으로 본다. */
    Optional<OrderCreation> findByOrderId(UUID orderId);

    Optional<OrderCreation> findByKeyIncludingDeleted(UUID ordererId, String key);
}
