package com.sparta.logistics.hub.presentation;

import com.sparta.logistics.hub.application.HubRouteQueryService;
import com.sparta.logistics.hub.application.dto.InternalHubRouteResult;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 서비스 간 호출 전용(I-03). Gateway에 라우팅이 없고, 성공 응답은 DTO를 감싸지 않고 반환한다(ADR 0010). */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/hub-routes")
public class InternalHubRouteController {

    private final HubRouteQueryService hubRouteQueryService;

    /** 공급업체 허브(departureHubId) → 요청업체 허브(arrivalHubId) 경로 1건. 두 파라미터는 필수다. */
    @GetMapping
    public InternalHubRouteResult getHubRoute(@RequestParam UUID departureHubId, @RequestParam UUID arrivalHubId) {
        return hubRouteQueryService.getInternalHubRoute(departureHubId, arrivalHubId);
    }
}
