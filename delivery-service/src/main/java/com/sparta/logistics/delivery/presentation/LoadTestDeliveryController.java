package com.sparta.logistics.delivery.presentation;

import com.sparta.logistics.common.auth.RequireRole;
import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.delivery.application.DeliveryCreationService;
import com.sparta.logistics.delivery.application.dto.CreateDeliveryResult;
import com.sparta.logistics.delivery.application.dto.DeliveryStatusResult;
import com.sparta.logistics.delivery.presentation.dto.CreateDeliveryRequest;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 부하 테스트 전용 배송 생성(I-09와 같은 처리). 주문을 거치지 않고 Gateway로 배송 생성만 측정하기 위해 둔다(결정 로그 24).
 * SA에 없는 API라 외부 계약이 아니다. 기본으로 꺼져 있고 delivery.load-test.enabled=true일 때만 빈이 등록된다.
 *
 * Note: 배송 생성은 원래 내부 API로만 연다(SA 6.2). 이 컨트롤러는 그 경계를 일부러 여는 것이라 세 겹으로 막는다.
 * 1) 프로퍼티가 없으면 빈 자체가 없어 매핑되지 않는다(405), 2) MASTER만 호출, 3) Swagger 문서에 싣지 않는다.
 */
@Hidden
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/deliveries/load-test")
@ConditionalOnProperty(prefix = "delivery.load-test", name = "enabled", havingValue = "true")
public class LoadTestDeliveryController {

    private final DeliveryCreationService deliveryCreationService;

    /** 배송 생성. 요청·멱등 규칙은 I-09와 같다. 새로 만들면 201, 같은 orderId 배송이 있으면 200 */
    @RequireRole(Role.MASTER)
    @PostMapping
    public ResponseEntity<ApiResponse<DeliveryStatusResult>> create(@Valid @RequestBody CreateDeliveryRequest request) {
        CreateDeliveryResult result = deliveryCreationService.create(request.toCommand());
        if (result.created()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created("배송이 생성되었습니다.", result.delivery()));
        }
        return ResponseEntity.ok(ApiResponse.ok(result.delivery()));
    }
}
