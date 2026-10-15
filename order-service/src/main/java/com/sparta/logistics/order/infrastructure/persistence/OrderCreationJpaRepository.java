package com.sparta.logistics.order.infrastructure.persistence;

import com.sparta.logistics.order.domain.OrderCreation;
import com.sparta.logistics.order.domain.OrderCreationRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderCreationJpaRepository extends JpaRepository<OrderCreation, UUID>, OrderCreationRepository {

    // Note: 주문 INSERT와 결과 확인이 같은 진행 기록을 잠가, 미완료 트랜잭션을 주문 없음으로 오판하지 않게 한다.
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OrderCreation c where c.orderId = :orderId")
    Optional<OrderCreation> findByOrderIdForUpdate(@Param("orderId") UUID orderId);
}
