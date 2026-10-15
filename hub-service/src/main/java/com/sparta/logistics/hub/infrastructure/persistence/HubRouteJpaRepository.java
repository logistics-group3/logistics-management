package com.sparta.logistics.hub.infrastructure.persistence;

import com.sparta.logistics.hub.domain.HubRoute;
import com.sparta.logistics.hub.domain.HubRouteRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HubRouteJpaRepository extends JpaRepository<HubRoute, UUID>, HubRouteRepository, HubRouteQueryRepository {

    // Note: 단건도 응답에 두 허브명이 필요하므로 EntityGraph로 한 번에 읽는다. 없으면 허브를 읽을 때마다 추가 쿼리가 나간다.
    @Override
    @EntityGraph(attributePaths = {"departureHub", "arrivalHub"})
    Optional<HubRoute> findById(UUID hubRouteId);
}
