package com.sparta.logistics.delivery.infrastructure.persistence;

import com.sparta.logistics.delivery.domain.DeliveryCreationClosure;
import com.sparta.logistics.delivery.domain.DeliveryCreationClosureRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** DeliveryCreationClosureRepository 구현. PostgreSQL 전용 SQL(ON CONFLICT, advisory lock)을 쓴다. */
public interface DeliveryCreationClosureJpaRepository
        extends JpaRepository<DeliveryCreationClosure, UUID>, DeliveryCreationClosureRepository {

    @Override
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO p_delivery_creation_closures (order_id, closed_at, closed_by)
            VALUES (:orderId, now(), :closedBy)
            ON CONFLICT (order_id) DO NOTHING
            """, nativeQuery = true)
    void close(@Param("orderId") UUID orderId, @Param("closedBy") String closedBy);

    /** Note: 잠금 키는 UUID 문자열의 64비트 해시다. 해시가 겹치면 서로 다른 주문이 잠시 직렬화될 뿐 정합성은 그대로다. */
    @Override
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(CAST(:orderId AS text), 0))) AS acquired",
            nativeQuery = true)
    int lockOrder(@Param("orderId") UUID orderId);
}
