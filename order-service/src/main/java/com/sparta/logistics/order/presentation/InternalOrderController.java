package com.sparta.logistics.order.presentation;

import com.sparta.logistics.order.application.OrderCreationQueryService;
import com.sparta.logistics.order.application.dto.OrderCreationResult;
import com.sparta.logistics.order.application.notification.AssignmentNotificationService;
import com.sparta.logistics.order.presentation.dto.AssignmentNotificationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * delivery 전용 내부 API(I-20, I-21, delivery 결정 로그 29). Gateway에 라우트가 없어 외부에서 부를 수 없다.
 * DTO를 감싸지 않고 반환한다(ADR 0010).
 */
@RestController
@Tag(name = "내부 주문 API")
@RequiredArgsConstructor
@RequestMapping("/internal/orders")
public class InternalOrderController {

    private final AssignmentNotificationService assignmentNotificationService;
    private final OrderCreationQueryService orderCreationQueryService;

    /**
     * I-20 배정 결과 알림. ASSIGNED면 담당자에게 DM, MANUAL_REQUIRED면 MASTER 채널에 수동 지정 요청을 보낸다.
     * 발송은 비동기라 받으면 바로 202다. 주문이 없거나 취소됐으면 보내지 않는다.
     */
    @Operation(summary = "I-20 배정 결과 알림")
    @PostMapping("/{orderId}/assignment-notifications")
    public ResponseEntity<Void> notifyAssignment(@PathVariable UUID orderId,
                                                 @Valid @RequestBody AssignmentNotificationRequest request) {
        assignmentNotificationService.accept(request.toNotification(orderId));
        return ResponseEntity.accepted().build();
    }

    /** I-21 주문 생성 결과. delivery가 확정(I-19)을 받지 못한 배송의 주문이 커밋됐는지 확인한다 */
    @Operation(summary = "I-21 주문 생성 결과 조회")
    @GetMapping("/{orderId}/creation-result")
    public OrderCreationResult getCreationResult(@PathVariable UUID orderId) {
        return orderCreationQueryService.getCreationResult(orderId);
    }
}
