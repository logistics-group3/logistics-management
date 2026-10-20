package com.sparta.logistics.order.presentation;

import com.sparta.logistics.common.auth.CurrentUser;
import com.sparta.logistics.common.auth.RequireRole;
import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.common.response.PageResponse;
import com.sparta.logistics.order.application.OrderCreationRecoveryService;
import com.sparta.logistics.order.presentation.dto.OrderCreationRecoveryResponse;
import com.sparta.logistics.order.presentation.dto.OrderRecoveryCandidateResponse;
import com.sparta.logistics.order.presentation.dto.RecoverOrderCreationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequireRole(Role.MASTER)
@RequestMapping("/api/v1/orders")
@Tag(name = "주문 생성 복구")
public class OrderCreationRecoveryController {
    private final OrderCreationRecoveryService recovery;

    @Operation(summary = "O-08 미해결 주문 생성 조사 목록")
    @GetMapping("/creation-recoveries/candidates")
    public ApiResponse<PageResponse<OrderRecoveryCandidateResponse>> candidates(@CurrentUser UserContext user, Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(recovery.candidates(user, pageable), OrderRecoveryCandidateResponse::from));
    }

    @Operation(summary = "O-09 주문 생성 시도 복구·명시적 재개")
    @PostMapping("/{orderId}/creation-recoveries")
    public ApiResponse<OrderCreationRecoveryResponse> recover(@CurrentUser UserContext user, @PathVariable UUID orderId,
            @Valid @RequestBody RecoverOrderCreationRequest request) {
        return ApiResponse.ok(OrderCreationRecoveryResponse.from(recovery.recover(user, orderId, request.toCommand())));
    }

    @Operation(summary = "O-10 복구 결과와 감사 이력 조회")
    @GetMapping("/creation-recoveries/{recoveryId}")
    public ApiResponse<OrderCreationRecoveryResponse> get(@CurrentUser UserContext user, @PathVariable UUID recoveryId) {
        return ApiResponse.ok(OrderCreationRecoveryResponse.from(recovery.get(user, recoveryId)));
    }
}
