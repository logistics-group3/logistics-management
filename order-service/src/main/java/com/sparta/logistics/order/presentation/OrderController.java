package com.sparta.logistics.order.presentation;

import com.sparta.logistics.common.auth.CurrentUser;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.common.response.PageResponse;
import com.sparta.logistics.order.application.OrderCancellationService;
import com.sparta.logistics.order.application.OrderQueryService;
import com.sparta.logistics.order.application.OrderService;
import com.sparta.logistics.order.domain.OrderRequestIdentity;
import com.sparta.logistics.order.presentation.dto.CancelOrderResponse;
import com.sparta.logistics.order.presentation.dto.CreateOrderRequest;
import com.sparta.logistics.order.presentation.dto.OrderResponse;
import com.sparta.logistics.order.presentation.dto.OrderSearchRequest;
import com.sparta.logistics.order.presentation.dto.UpdateOrderRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "주문", description = "주문 생성·검색·단건 조회·요청사항 수정·취소")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderQueryService orderQueryService;
    private final OrderCancellationService orderCancellationService;

    @Operation(summary = "O-01 주문 생성", description = "요청업체 관리자 본인 또는 MASTER가 주문한다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> create(@CurrentUser UserContext user,
            @Parameter(description = "새 주문마다 새 키, 재시도는 같은 키와 본문. UUID 권장", example = "8f2d6f94-a287-4b6d-82c9-4828a2a72ef3")
            @RequestHeader("Idempotency-Key") @NotBlank(message = "Idempotency-Key는 필수입니다.")
            @Size(min = 1, max = OrderRequestIdentity.MAX_KEY_LENGTH, message = "멱등키는 1~128자여야 합니다.")
            @Pattern(regexp = OrderRequestIdentity.KEY_PATTERN, message = "멱등키는 영문·숫자와 . _ : - 만 사용할 수 있습니다.") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.created("주문이 생성되었습니다.", OrderResponse.from(orderService.create(user, idempotencyKey, request.toCommand())));
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

    @Operation(summary = "O-05 주문 취소", description = "주문자 본인 또는 MASTER가 집하 시작 전 주문을 취소한다.")
    @PostMapping("/{orderId}/cancel")
    public ApiResponse<CancelOrderResponse> cancel(@CurrentUser UserContext user, @PathVariable UUID orderId) {
        return ApiResponse.ok("주문이 취소되었습니다.", CancelOrderResponse.from(orderCancellationService.cancel(user, orderId)));
    }
}
