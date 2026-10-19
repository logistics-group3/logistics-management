package com.sparta.logistics.order.domain;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface OrderCancellationRepository {

    OrderCancellation saveAndFlush(OrderCancellation cancellation);

    Optional<OrderCancellation> findByIdForUpdate(UUID id);

    boolean existsByOrderIdAndStatusIn(UUID orderId, Collection<OrderCancellationStatus> statuses);

    void flush();
}
