package com.sparta.logistics.order.domain;

import java.util.Optional;
import java.util.UUID;

public interface OrderDeletionRepository {
    OrderDeletion saveAndFlush(OrderDeletion deletion);

    Optional<OrderDeletion> findByOrderIdIncludingDeleted(UUID orderId);

    void flush();
}
