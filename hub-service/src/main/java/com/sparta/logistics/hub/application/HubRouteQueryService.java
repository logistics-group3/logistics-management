package com.sparta.logistics.hub.application;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.hub.application.dto.HubRouteResult;
import com.sparta.logistics.hub.application.dto.InternalHubRouteResult;
import com.sparta.logistics.hub.domain.HubErrorCode;
import com.sparta.logistics.hub.domain.HubRouteRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HubRouteQueryService {

    private final HubRouteRepository hubRouteRepository;

    /** H-03 경로 검색. 출발·도착 허브 ID는 선택 조건이고 둘 다 없으면 전체를 조회한다. */
    public Page<HubRouteResult> search(UUID departureHubId, UUID arrivalHubId, Pageable pageable) {
        return hubRouteRepository.search(departureHubId, arrivalHubId, pageable).map(HubRouteResult::from);
    }

    /** I-03 내부 경로 조회. 출발·도착 허브 쌍의 경로 1건을 돌려주고, 없으면 404 HUB_ROUTE_NOT_FOUND다. 같은 허브 쌍도 경로가 없으므로 같은 오류다. */
    @Cacheable(cacheNames = HubCacheNames.INTERNAL_HUB_ROUTE, key = "#departureHubId + ':' + #arrivalHubId")
    public InternalHubRouteResult getInternalHubRoute(UUID departureHubId, UUID arrivalHubId) {
        return hubRouteRepository.findByDepartureHubIdAndArrivalHubId(departureHubId, arrivalHubId)
                .map(InternalHubRouteResult::from)
                .orElseThrow(() -> new BusinessException(HubErrorCode.HUB_ROUTE_NOT_FOUND));
    }

    /** H-04 경로 단건 조회. 논리 삭제된 경로는 @SQLRestriction으로 조회되지 않아 404 HUB_ROUTE_NOT_FOUND가 된다. */
    @Cacheable(HubCacheNames.HUB_ROUTE)
    public HubRouteResult getHubRoute(UUID hubRouteId) {
        return hubRouteRepository.findById(hubRouteId)
                .map(HubRouteResult::from)
                .orElseThrow(() -> new BusinessException(HubErrorCode.HUB_ROUTE_NOT_FOUND));
    }
}
