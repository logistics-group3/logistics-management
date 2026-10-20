package com.sparta.logistics.hub.presentation;

import com.sparta.logistics.common.auth.CurrentUser;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.common.response.PageResponse;
import com.sparta.logistics.hub.application.StockQueryService;
import com.sparta.logistics.hub.application.StockRegistrationService;
import com.sparta.logistics.hub.presentation.dto.HubStockResponse;
import com.sparta.logistics.hub.presentation.dto.InboundStockRequest;
import com.sparta.logistics.hub.presentation.dto.InboundStockResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 허브 재고 검색(H-05)과 입고(H-06). 재고 관리 고도화 API다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/hubs/{hubId}/stocks")
public class HubStockController {

    private final StockQueryService stockQueryService;
    private final StockRegistrationService stockRegistrationService;

    /** H-05. 로그인 사용자 모두 조회한다. page, size(10·30·50), sort(createdAt·quantity)는 공통 Pageable 처리를 따른다. */
    @GetMapping
    public ApiResponse<PageResponse<HubStockResponse>> search(
            @PathVariable UUID hubId, @RequestParam(required = false) UUID productId, Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(stockQueryService.search(hubId, productId, pageable), HubStockResponse::from));
    }

    /** H-06. MASTER 또는 해당 상품 업체의 관리자만 입고한다. 업체 관리자 확인은 company 데이터가 필요해 서비스에서 한다. */
    @PostMapping("/inbound")
    public ApiResponse<InboundStockResponse> inbound(@CurrentUser UserContext user, @PathVariable UUID hubId,
            @Valid @RequestBody InboundStockRequest request) {
        return ApiResponse.ok("재고가 입고되었습니다.",
                InboundStockResponse.from(stockRegistrationService.inbound(user, request.toCommand(hubId))));
    }
}
