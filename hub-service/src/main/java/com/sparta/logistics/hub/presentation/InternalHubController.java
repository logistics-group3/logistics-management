package com.sparta.logistics.hub.presentation;

import com.sparta.logistics.hub.application.HubQueryService;
import com.sparta.logistics.hub.application.dto.InternalHubResult;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 서비스 간 호출 전용(I-02). Gateway에 라우팅이 없고, 성공 응답은 DTO를 감싸지 않고 반환한다(ADR 0010). */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/hubs")
public class InternalHubController {

    private final HubQueryService hubQueryService;

    @GetMapping("/{hubId}")
    public InternalHubResult getHub(@PathVariable UUID hubId) {
        return hubQueryService.getInternalHub(hubId);
    }
}
