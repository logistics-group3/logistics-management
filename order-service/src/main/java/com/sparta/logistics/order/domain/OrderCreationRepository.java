package com.sparta.logistics.order.domain;

import java.util.Optional;
import java.util.UUID;

public interface OrderCreationRepository {

    OrderCreation saveAndFlush(OrderCreation creation);

    Optional<OrderCreation> findByOrderIdForUpdate(UUID orderId);

    Optional<OrderCreation> findByKeyIncludingDeleted(UUID ordererId, String key);
}
