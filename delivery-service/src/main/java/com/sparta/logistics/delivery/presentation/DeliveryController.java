package com.sparta.logistics.delivery.presentation;

import com.sparta.logistics.common.auth.CurrentUser;
import com.sparta.logistics.common.auth.RequireRole;
import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.common.response.PageResponse;
import com.sparta.logistics.delivery.application.DeliveryQueryService;
import com.sparta.logistics.delivery.application.DeliveryService;
import com.sparta.logistics.delivery.presentation.dto.ChangeRouteStatusRequest;
import com.sparta.logistics.delivery.application.dto.DeliveryResponse;
import com.sparta.logistics.delivery.presentation.dto.DeliverySearchRequest;
import com.sparta.logistics.delivery.application.dto.DeliverySummaryResult;
import com.sparta.logistics.delivery.presentation.dto.ReassignManagerRequest;
import com.sparta.logistics.delivery.presentation.dto.UpdateDeliveryRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 배송 외부 API(SA 6.8). 배송 생성·삭제는 외부에 열지 않는다(SA 6.2). */
@Tag(name = "Delivery", description = "배송 조회, 배송지 수정, 구간 담당자 재배정, 구간 상태 변경")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/deliveries")
public class DeliveryController {

    private final DeliveryQueryService deliveryQueryService;
    private final DeliveryService deliveryService;

    @Operation(summary = "D-01 배송 검색")
    @GetMapping
    public ApiResponse<PageResponse<DeliverySummaryResult>> search(
            @CurrentUser UserContext user, @ModelAttribute DeliverySearchRequest request, Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(deliveryQueryService.search(user, request.toCondition(), pageable)));
    }

    @Operation(summary = "D-02 배송 단건 조회")
    @GetMapping("/{deliveryId}")
    public ApiResponse<DeliveryResponse> get(@CurrentUser UserContext user, @PathVariable UUID deliveryId) {
        return ApiResponse.ok(deliveryQueryService.getDelivery(user, deliveryId));
    }

    @Operation(summary = "D-03 배송지·수령인 수정")
    @PatchMapping("/{deliveryId}")
    public ApiResponse<DeliveryResponse> update(@CurrentUser UserContext user, @PathVariable UUID deliveryId,
                                                @Valid @RequestBody UpdateDeliveryRequest request) {
        return ApiResponse.ok(deliveryService.updateRecipient(user, deliveryId, request.toCommand()));
    }

    @Operation(summary = "D-04 구간 담당자 재배정")
    @RequireRole(Role.MASTER)
    @PostMapping("/{deliveryId}/routes/{routeId}/manager")
    public ApiResponse<DeliveryResponse> reassignManager(@PathVariable UUID deliveryId, @PathVariable UUID routeId,
                                                         @Valid @RequestBody ReassignManagerRequest request) {
        return ApiResponse.ok(deliveryService.reassignManager(deliveryId, routeId, request.managerId()));
    }

    @Operation(summary = "D-05 구간 상태 변경")
    @PatchMapping("/{deliveryId}/routes/{routeId}/status")
    public ApiResponse<DeliveryResponse> changeRouteStatus(@CurrentUser UserContext user, @PathVariable UUID deliveryId,
                                                           @PathVariable UUID routeId,
                                                           @Valid @RequestBody ChangeRouteStatusRequest request) {
        return ApiResponse.ok(deliveryService.changeRouteStatus(user, deliveryId, routeId, request.toCommand()));
    }
}
