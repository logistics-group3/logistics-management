package com.sparta.logistics.order.presentation;

import com.sparta.logistics.common.auth.CurrentUser;
import com.sparta.logistics.common.auth.RequireRole;
import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.order.application.OrderDeletionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "주문", description = "주문 생성·조회·수정·취소·삭제")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrderDeletionController {
    private final OrderDeletionService service;

    @Operation(summary = "O-06 주문 삭제", description = "MASTER가 취소 또는 배송 완료 주문과 배송을 논리 삭제한다.")
    @RequireRole(Role.MASTER)
    @DeleteMapping("/{orderId}")
    public ApiResponse<Void> delete(@CurrentUser UserContext user, @PathVariable UUID orderId) {
        service.delete(user, orderId);
        return ApiResponse.ok("주문이 삭제되었습니다.", null);
    }
}
