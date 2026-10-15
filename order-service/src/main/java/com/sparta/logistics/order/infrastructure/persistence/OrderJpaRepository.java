package com.sparta.logistics.order.infrastructure.persistence;

import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.domain.OrderRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderJpaRepository extends JpaRepository<Order, UUID>, OrderRepository {

    // Note: 같은 주문의 상태 확인과 변경을 한 트랜잭션에서 직렬화한다. 후속 취소·삭제도 같은 잠금 규칙을 따른다.
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    // Note: 삭제 커밋 재확인은 논리 삭제된 주문도 잠가 읽어야 한다. 일반 주문 조회에는 사용하지 않는다.
    @Override
    @Query(value = "SELECT * FROM {h-schema}p_orders WHERE order_id = :id FOR UPDATE", nativeQuery = true)
    Optional<Order> findByIdIncludingDeletedForUpdate(@Param("id") UUID id);
}
