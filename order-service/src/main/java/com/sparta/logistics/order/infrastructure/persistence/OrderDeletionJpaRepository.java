package com.sparta.logistics.order.infrastructure.persistence;

import com.sparta.logistics.order.domain.OrderDeletion;
import com.sparta.logistics.order.domain.OrderDeletionRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderDeletionJpaRepository extends JpaRepository<OrderDeletion, UUID>, OrderDeletionRepository {
    // Note: 모든 기록 변경은 같은 주문 행의 잠금 아래 수행한다. 삭제된 진행 기록도 임의 재시작하지 않는다.
    @Override
    @Query(value = "SELECT * FROM {h-schema}p_order_deletions WHERE order_id = :orderId", nativeQuery = true)
    Optional<OrderDeletion> findByOrderIdIncludingDeleted(@Param("orderId") UUID orderId);
}
