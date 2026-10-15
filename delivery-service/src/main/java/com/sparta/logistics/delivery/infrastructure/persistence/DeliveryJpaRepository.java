package com.sparta.logistics.delivery.infrastructure.persistence;

import com.sparta.logistics.delivery.domain.ActiveRouteCount;
import com.sparta.logistics.delivery.domain.Delivery;
import com.sparta.logistics.delivery.domain.DeliveryRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** DeliveryRepository 구현. search는 DeliveryQueryRepositoryImpl(QueryDSL)이 맡는다. */
public interface DeliveryJpaRepository extends JpaRepository<Delivery, UUID>, DeliveryRepository, DeliveryQueryRepository {

    /**
     * Note: 후보 전원의 건수를 GROUP BY 한 번으로 센다. 후보마다 count 쿼리를 보내면 후보 수만큼 왕복한다.
     * JPQL이라 @SQLRestriction이 적용되어 논리 삭제된 구간은 세지 않는다. manager_id 인덱스(V1)를 탄다.
     */
    @Override
    @Query("""
            SELECT new com.sparta.logistics.delivery.domain.ActiveRouteCount(r.managerId, COUNT(r))
            FROM DeliveryRoute r
            WHERE r.managerId IN :managerIds
              AND r.status IN (com.sparta.logistics.delivery.domain.RouteStatus.WAITING,
                               com.sparta.logistics.delivery.domain.RouteStatus.MOVING)
            GROUP BY r.managerId
            """)
    List<ActiveRouteCount> countActiveRoutes(@Param("managerIds") Collection<UUID> managerIds);
}
