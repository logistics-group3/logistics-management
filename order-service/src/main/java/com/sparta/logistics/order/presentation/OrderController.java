package com.sparta.logistics.order.presentation;

import com.sparta.logistics.common.auth.CurrentUser;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.order.application.OrderService;
import com.sparta.logistics.order.presentation.dto.CreateOrderRequest;
import com.sparta.logistics.order.presentation.dto.OrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "주문", description = "주문 생성")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "O-01 주문 생성", description = "요청업체 관리자 본인 또는 MASTER가 주문한다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> create(@CurrentUser UserContext user, @Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.created("주문이 생성되었습니다.", OrderResponse.from(orderService.create(user, request.toCommand())));
    }
}
