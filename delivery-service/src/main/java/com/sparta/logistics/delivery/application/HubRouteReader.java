package com.sparta.logistics.delivery.application;

import com.sparta.logistics.delivery.domain.RouteInfo;
import java.util.UUID;

/** 허브 간 경로 조회(hub I-03). 구현은 infrastructure/client. */
public interface HubRouteReader {

    /** 공급업체 허브 → 요청업체 허브의 예상 거리·시간. 허브 이동 구간의 expected 값이 된다. */
    RouteInfo getExpectedRoute(UUID departureHubId, UUID arrivalHubId);
}
