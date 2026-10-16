package com.sparta.logistics.hub.presentation;

import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.common.response.PageResponse;
import com.sparta.logistics.hub.application.HubRouteQueryService;
import com.sparta.logistics.hub.presentation.dto.HubRouteResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 허브 간 경로 조회(H-03, H-04). 로그인 사용자에게 열려 있고 인증은 Gateway가 처리한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/hub-routes")
public class HubRouteController {

    private final HubRouteQueryService hubRouteQueryService;

    /** page, size(10·30·50), sort(createdAt·distanceKm·durationMinutes)는 공통 Pageable 처리를 따른다. */
    @GetMapping
    public ApiResponse<PageResponse<HubRouteResponse>> search(
            @RequestParam(required = false) UUID departureHubId,
            @RequestParam(required = false) UUID arrivalHubId,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(hubRouteQueryService.search(departureHubId, arrivalHubId, pageable), HubRouteResponse::from));
    }

    @GetMapping("/{hubRouteId}")
    public ApiResponse<HubRouteResponse> getHubRoute(@PathVariable UUID hubRouteId) {
        return ApiResponse.ok(HubRouteResponse.from(hubRouteQueryService.getHubRoute(hubRouteId)));
    }
}
