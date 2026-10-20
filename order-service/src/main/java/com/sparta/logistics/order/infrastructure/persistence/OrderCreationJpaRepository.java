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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderCreationJpaRepository extends JpaRepository<OrderCreation, UUID>, OrderCreationRepository {

    // Note: 주문 INSERT와 결과 확인이 같은 진행 기록을 잠가, 미완료 트랜잭션을 주문 없음으로 오판하지 않게 한다.
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OrderCreation c where c.orderId = :orderId")
    Optional<OrderCreation> findByOrderIdForUpdate(@Param("orderId") UUID orderId);

    // Note: 논리 삭제로 사용한 키가 다시 실행되지 않게 기록을 포함해서 읽는다. SQLRestriction을 적용하지 않는 조회다.
    @Override
    @Query(value = "SELECT * FROM {h-schema}p_order_creations WHERE orderer_id = :ordererId AND idempotency_key = :key", nativeQuery = true)
    Optional<OrderCreation> findByKeyIncludingDeleted(@Param("ordererId") UUID ordererId, @Param("key") String key);

    @Override
    @Query("select c from OrderCreation c where c.status in ('PROCESSING', 'RECOVERY_REQUIRED')")
    Page<OrderCreation> findRecoveryCandidates(Pageable pageable);
}
