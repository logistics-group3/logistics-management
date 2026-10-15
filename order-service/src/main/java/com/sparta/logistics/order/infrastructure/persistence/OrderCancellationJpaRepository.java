package com.sparta.logistics.order.infrastructure.persistence;

import com.sparta.logistics.order.domain.OrderCancellation;
import com.sparta.logistics.order.domain.OrderCancellationRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderCancellationJpaRepository extends JpaRepository<OrderCancellation, UUID>, OrderCancellationRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OrderCancellation c where c.id = :id")
    Optional<OrderCancellation> findByIdForUpdate(@Param("id") UUID id);
}
