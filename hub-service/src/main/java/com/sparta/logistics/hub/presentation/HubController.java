package com.sparta.logistics.hub.presentation;

import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.common.response.PageResponse;
import com.sparta.logistics.hub.application.HubQueryService;
import com.sparta.logistics.hub.application.dto.HubResult;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 허브 조회(H-01, H-02). 로그인 사용자에게 열려 있고 인증은 Gateway가 처리한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/hubs")
public class HubController {

    private final HubQueryService hubQueryService;

    /** page, size(10·30·50), sort(createdAt·name)는 공통 Pageable 처리를 따른다. */
    @GetMapping
    public ApiResponse<PageResponse<HubResult>> search(@RequestParam(required = false) String keyword, Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(hubQueryService.search(keyword, pageable)));
    }

    @GetMapping("/{hubId}")
    public ApiResponse<HubResult> getHub(@PathVariable UUID hubId) {
        return ApiResponse.ok(hubQueryService.getHub(hubId));
    }
}
