package com.sparta.logistics.order.domain;

import java.util.Optional;
import java.util.UUID;

public interface OrderCreationRecoveryRepository {
    OrderCreationRecovery saveAndFlush(OrderCreationRecovery recovery);
    Optional<OrderCreationRecovery> findById(UUID id);
    Optional<OrderCreationRecovery> findByIdForUpdate(UUID id);
}
