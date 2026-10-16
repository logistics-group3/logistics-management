package com.sparta.logistics.hub.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface HubRouteRepository {

    Optional<HubRoute> findById(UUID hubRouteId);

    Page<HubRoute> search(UUID departureHubId, UUID arrivalHubId, Pageable pageable);

    /** 출발·도착 허브 쌍의 활성 경로. 활성 경로는 방향별로 하나라 최대 1건이다(부분 유니크 인덱스). */
    Optional<HubRoute> findByDepartureHubIdAndArrivalHubId(UUID departureHubId, UUID arrivalHubId);
}
