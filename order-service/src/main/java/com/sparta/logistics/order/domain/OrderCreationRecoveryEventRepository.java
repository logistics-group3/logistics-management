package com.sparta.logistics.order.domain;

import java.util.List;
import java.util.UUID;

public interface OrderCreationRecoveryEventRepository {
    OrderCreationRecoveryEvent save(OrderCreationRecoveryEvent event);
    List<OrderCreationRecoveryEvent> findByRecoveryIdOrderByCreatedAtAsc(UUID recoveryId);
}
