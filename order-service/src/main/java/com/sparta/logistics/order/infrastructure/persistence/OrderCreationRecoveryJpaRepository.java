package com.sparta.logistics.order.infrastructure.persistence;

import com.sparta.logistics.order.domain.OrderCreationRecovery;
import com.sparta.logistics.order.domain.OrderCreationRecoveryRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderCreationRecoveryJpaRepository extends JpaRepository<OrderCreationRecovery, UUID>, OrderCreationRecoveryRepository {
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from OrderCreationRecovery r where r.id = :id")
    Optional<OrderCreationRecovery> findByIdForUpdate(@Param("id") UUID id);
}
