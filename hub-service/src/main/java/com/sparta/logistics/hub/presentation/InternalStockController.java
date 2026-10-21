package com.sparta.logistics.hub.presentation;

import com.sparta.logistics.hub.application.StockRegistrationService;
import com.sparta.logistics.hub.application.StockService;
import com.sparta.logistics.hub.application.dto.InternalStockResult;
import com.sparta.logistics.hub.application.dto.StockAllocationResult;
import com.sparta.logistics.hub.application.dto.StockRestoreResult;
import com.sparta.logistics.hub.presentation.dto.AllocateStockRequest;
import com.sparta.logistics.hub.presentation.dto.InitializeStockRequest;
import com.sparta.logistics.hub.presentation.dto.RestoreStockRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 서비스 간 호출 전용(I-04~I-06). Gateway에 라우팅이 없고, 성공 응답은 DTO를 감싸지 않고 반환한다(ADR 0010). */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/stocks")
public class InternalStockController {

    private final StockRegistrationService stockRegistrationService;
    private final StockService stockService;

    /** I-04 재고 초기화(company). 이미 있으면 기존 값을 반환한다. */
    @PostMapping
    public InternalStockResult initialize(@Valid @RequestBody InitializeStockRequest request) {
        return stockRegistrationService.initialize(request.toCommand());
    }

    /** I-05 재고 할당(order). 공급업체 허브 한 곳에서 차감하고, 같은 orderId 재요청은 처음 결과를 반환한다(ADR 0003). */
    @PostMapping("/allocate")
    public StockAllocationResult allocate(@Valid @RequestBody AllocateStockRequest request) {
        return stockService.allocate(request.toCommand());
    }

    /** I-06 재고 복원(order). 차감 이력이 없거나 이미 복원했으면 restoredQuantity는 0이다. */
    @PostMapping("/restore")
    public StockRestoreResult restore(@Valid @RequestBody RestoreStockRequest request) {
        return stockService.restore(request.orderId());
    }
}
