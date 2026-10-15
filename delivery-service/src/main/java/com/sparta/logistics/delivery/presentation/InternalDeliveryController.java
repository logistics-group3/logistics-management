package com.sparta.logistics.delivery.presentation;

import com.sparta.logistics.delivery.application.DeliveryCreationService;
import com.sparta.logistics.delivery.application.DeliveryQueryService;
import com.sparta.logistics.delivery.application.DeliveryService;
import com.sparta.logistics.delivery.application.dto.CreateDeliveryResult;
import com.sparta.logistics.delivery.presentation.dto.CreateDeliveryRequest;
import com.sparta.logistics.delivery.presentation.dto.DeliveryStatusResponse;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * order 전용 내부 API(I-09~I-13, SA 6.3). Gateway에 라우트가 없어 외부에서 부를 수 없다.
 * DTO를 감싸지 않고 반환한다(ADR 0010). I-09·I-11·I-12·I-13은 같은 요청을 다시 보내도 결과가 같다(SA 7.3).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/deliveries")
public class InternalDeliveryController {

    private final DeliveryCreationService deliveryCreationService;
    private final DeliveryService deliveryService;
    private final DeliveryQueryService deliveryQueryService;

    /** I-09 배송 생성. 새로 만들면 201, 같은 orderId 배송이 이미 있으면 200과 기존 값 */
    @PostMapping
    public ResponseEntity<DeliveryStatusResponse> create(@Valid @RequestBody CreateDeliveryRequest request) {
        CreateDeliveryResult result = deliveryCreationService.create(request.toCommand());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(DeliveryStatusResponse.from(result.delivery()));
    }

    /** I-10 배송 조회 */
    @GetMapping("/{deliveryId}")
    public DeliveryStatusResponse get(@PathVariable UUID deliveryId) {
        return DeliveryStatusResponse.from(deliveryQueryService.getDeliveryStatus(deliveryId));
    }

    /** I-11 배송 취소 */
    @PostMapping("/{deliveryId}/cancel")
    public DeliveryStatusResponse cancel(@PathVariable UUID deliveryId) {
        return DeliveryStatusResponse.from(deliveryService.cancel(deliveryId));
    }

    /** I-12 취소 원복(보상) */
    @PostMapping("/{deliveryId}/cancel-revert")
    public DeliveryStatusResponse revertCancel(@PathVariable UUID deliveryId) {
        return DeliveryStatusResponse.from(deliveryService.revertCancel(deliveryId));
    }

    /** I-13 배송 삭제(주문 삭제·생성 보상). 대상이 없어도 204 */
    @DeleteMapping("/orders/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteByOrderId(@PathVariable UUID orderId) {
        deliveryService.deleteByOrderId(orderId);
    }
}
