package com.sparta.logistics.order.presentation;

import com.sparta.logistics.common.auth.CurrentUser;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.common.response.PageResponse;
import com.sparta.logistics.order.application.OrderQueryService;
import com.sparta.logistics.order.application.OrderService;
import com.sparta.logistics.order.presentation.dto.CreateOrderRequest;
import com.sparta.logistics.order.presentation.dto.OrderResponse;
import com.sparta.logistics.order.presentation.dto.OrderSearchRequest;
import com.sparta.logistics.order.presentation.dto.UpdateOrderRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "주문", description = "주문 생성·검색·단건 조회·요청사항 수정")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderQueryService orderQueryService;

    @Operation(summary = "O-01 주문 생성", description = "요청업체 관리자 본인 또는 MASTER가 주문한다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> create(@CurrentUser UserContext user, @Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.created("주문이 생성되었습니다.", OrderResponse.from(orderService.create(user, request.toCommand())));
    }

    @Operation(summary = "O-02 주문 검색", description = "USER는 본인 주문만, MASTER는 전체 주문을 검색한다. 정렬은 createdAt·quantity를 허용한다.")
    @GetMapping
    public ApiResponse<PageResponse<OrderResponse>> search(@CurrentUser UserContext user,
            @Valid @ModelAttribute @ParameterObject OrderSearchRequest request,
            // Note: 공통 리졸버의 기본값을 Swagger에도 명시한다. 미지정하면 문서에는 size=20이 표시된다.
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(orderQueryService.search(user, request.toCondition(), pageable), OrderResponse::from));
    }

    @Operation(summary = "O-03 주문 단건 조회", description = "주문자 본인 또는 MASTER가 조회한다.")
    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> getOrder(@CurrentUser UserContext user, @PathVariable UUID orderId) {
        return ApiResponse.ok(OrderResponse.from(orderQueryService.getOrder(user, orderId)));
    }

    @Operation(summary = "O-04 요청사항 수정", description = "주문자 본인 또는 MASTER가 요청사항만 수정한다. 취소된 주문은 수정할 수 없다.")
    @PatchMapping("/{orderId}")
    public ApiResponse<OrderResponse> update(@CurrentUser UserContext user, @PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderRequest request) {
        return ApiResponse.ok(OrderResponse.from(orderService.updateRequestNote(user, orderId, request.toCommand())));
    }
}
