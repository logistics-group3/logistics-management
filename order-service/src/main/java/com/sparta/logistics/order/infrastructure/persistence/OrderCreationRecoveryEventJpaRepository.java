package com.sparta.logistics.order.infrastructure.persistence;

import com.sparta.logistics.order.domain.OrderCreationRecoveryEvent;
import com.sparta.logistics.order.domain.OrderCreationRecoveryEventRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderCreationRecoveryEventJpaRepository extends JpaRepository<OrderCreationRecoveryEvent, UUID>, OrderCreationRecoveryEventRepository {
}
