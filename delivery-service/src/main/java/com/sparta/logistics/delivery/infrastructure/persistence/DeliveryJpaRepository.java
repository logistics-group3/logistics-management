package com.sparta.logistics.delivery.infrastructure.persistence;

import com.sparta.logistics.delivery.domain.Delivery;
import com.sparta.logistics.delivery.domain.DeliveryRepository;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** DeliveryRepository 구현. Spring Data JPA가 메서드 이름과 @Query로 구현을 만든다(연결용 구현 클래스를 두지 않는다). */
public interface DeliveryJpaRepository extends JpaRepository<Delivery, UUID>, DeliveryRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Delivery d where d.id = :id")
    Optional<Delivery> findByIdForUpdate(@Param("id") UUID id);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Delivery d where d.orderId = :orderId")
    Optional<Delivery> findByOrderIdForUpdate(@Param("orderId") UUID orderId);

    /** Note: 키는 허브 UUID 문자열의 64비트 해시다. orderId 잠금(시드 0)과 겹치지 않게 시드 1을 쓴다. 겹쳐도 한쪽이 한 번 건너뛸 뿐이다. */
    @Override
    @Query(value = "SELECT pg_try_advisory_xact_lock(hashtextextended(CAST(:hubId AS text), 1))", nativeQuery = true)
    boolean tryLockHubAssignment(@Param("hubId") UUID hubId);

    @Override
    @Query(value = """
            SELECT d.delivery_id
            FROM p_deliveries d
            WHERE d.deleted_at IS NULL
              AND EXISTS (SELECT 1 FROM p_delivery_routes r
                          WHERE r.delivery_id = d.delivery_id AND r.managing_hub_id = :hubId
                            AND r.manager_id IS NULL AND r.status = 'WAITING' AND r.deleted_at IS NULL)
            ORDER BY d.created_at
            LIMIT :limit
            FOR UPDATE OF d SKIP LOCKED
            """, nativeQuery = true)
    List<UUID> lockDeliveriesAwaitingManager(@Param("hubId") UUID hubId, @Param("limit") int limit);

    @Override
    @Query("select distinct d from Delivery d join fetch d.routes where d.id in :ids")
    List<Delivery> findAllWithRoutesByIdIn(@Param("ids") Collection<UUID> ids);
}
