package com.sparta.logistics.delivery.presentation;

import com.sparta.logistics.delivery.application.DeliveryCreationService;
import com.sparta.logistics.delivery.application.DeliveryQueryService;
import com.sparta.logistics.delivery.application.DeliveryService;
import com.sparta.logistics.delivery.application.assignment.DeliveryConfirmationService;
import com.sparta.logistics.delivery.application.dto.CreateDeliveryResult;
import com.sparta.logistics.delivery.presentation.dto.CreateDeliveryRequest;
import com.sparta.logistics.delivery.application.dto.DeliveryOrderResult;
import com.sparta.logistics.delivery.application.dto.DeliveryStatusResult;
import com.sparta.logistics.delivery.application.dto.DeliveryNotificationRecipientsResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * order 전용 내부 API(I-09~I-13, I-15, I-17~I-19). Gateway에 라우트가 없어 외부에서 부를 수 없다.
 * DTO를 감싸지 않고 반환한다(ADR 0010). I-09·I-11·I-12·I-13·I-18·I-19는 같은 요청을 다시 보내도 결과가 같다(SA 7.3).
 */
@RestController
@Tag(name = "내부 배송 API")
@RequiredArgsConstructor
@RequestMapping("/internal/deliveries")
public class InternalDeliveryController {

    private final DeliveryCreationService deliveryCreationService;
    private final DeliveryService deliveryService;
    private final DeliveryQueryService deliveryQueryService;
    private final DeliveryConfirmationService deliveryConfirmationService;

    /** I-09 배송 생성. 새로 만들면 201, 같은 orderId 배송이 이미 있으면 200과 기존 값 */
    @PostMapping
    public ResponseEntity<DeliveryStatusResult> create(@Valid @RequestBody CreateDeliveryRequest request) {
        CreateDeliveryResult result = deliveryCreationService.create(request.toCommand());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.delivery());
    }

    /** I-10 배송 조회 */
    @GetMapping("/{deliveryId}")
    public DeliveryStatusResult get(@PathVariable UUID deliveryId) {
        return deliveryQueryService.getDeliveryStatus(deliveryId);
    }

    /** I-15 주문 커밋 후 알림 수신자 조회. 기존 I-10 응답은 유지한다. */
    @Operation(summary = "I-15 배송 알림 수신자 조회")
    @GetMapping("/{deliveryId}/notification-recipients")
    public DeliveryNotificationRecipientsResult getNotificationRecipients(@PathVariable UUID deliveryId) {
        return deliveryQueryService.getNotificationRecipients(deliveryId);
    }

    /** I-17 주문 생성 시도(orderId)의 배송 결과 확인. 배송 ID를 받지 못한 요청의 결과를 확인한다(이슈 #14). */
    @Operation(summary = "I-17 orderId로 배송 결과 조회")
    @GetMapping("/orders/{orderId}")
    public DeliveryOrderResult getByOrderId(@PathVariable UUID orderId) {
        return deliveryQueryService.getOrderResult(orderId);
    }

    /**
     * I-19 주문 확정. order가 주문을 커밋한 뒤 부르고, 이때부터 구간 담당자를 배정한다(결정 로그 29).
     * 배정 결과를 기다리지 않고 확정이 커밋되면 200이다. 이미 확정됐으면 같은 응답, 배송이 없으면 404 DELIVERY_NOT_FOUND
     */
    @Operation(summary = "I-19 주문 확정(배정 시작)")
    @PostMapping("/orders/{orderId}/confirm")
    public DeliveryStatusResult confirm(@PathVariable UUID orderId) {
        return deliveryConfirmationService.confirm(orderId);
    }

    /** I-11 배송 취소 */
    @PostMapping("/{deliveryId}/cancel")
    public DeliveryStatusResult cancel(@PathVariable UUID deliveryId) {
        return deliveryService.cancel(deliveryId);
    }

    /** I-12 취소 원복(보상) */
    @PostMapping("/{deliveryId}/cancel-revert")
    public DeliveryStatusResult revertCancel(@PathVariable UUID deliveryId) {
        return deliveryService.revertCancel(deliveryId);
    }

    /** I-13 배송 삭제(주문 삭제·생성 보상). 대상이 없어도 204 */
    @DeleteMapping("/orders/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteByOrderId(@PathVariable UUID orderId) {
        deliveryService.deleteByOrderId(orderId);
    }

    @Operation(summary = "I-18 주문 생성 실패 배송 조건부 종료")
    @DeleteMapping("/orders/{orderId}/creation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void closeCreation(@PathVariable UUID orderId,
            @RequestParam(required = false) UUID expectedDeliveryId) {
        deliveryService.closeCreation(orderId, expectedDeliveryId);
    }
}
