package com.sparta.logistics.company.presentation;

import com.sparta.logistics.company.application.ProductQueryService;
import com.sparta.logistics.company.presentation.dto.InternalProductResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 서비스 간 호출 전용(I-08). 삭제된 상품은 404라 주문할 수 없다. 내부 API라 감싸지 않는다(ADR 0010). */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/products")
public class InternalProductController {

    private final ProductQueryService productQueryService;

    @GetMapping("/{productId}")
    public InternalProductResponse getProduct(@PathVariable UUID productId) {
        return InternalProductResponse.from(productQueryService.getInternalProduct(productId));
    }
}
