package com.sparta.logistics.order.infrastructure.client;

import com.sparta.logistics.common.feign.IdempotentRetryConfig;
import com.sparta.logistics.order.infrastructure.client.dto.CompanyResponse;
import com.sparta.logistics.order.infrastructure.client.dto.ProductResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "company-service", contextId = "order-company", path = "/internal",
        configuration = IdempotentRetryConfig.class)
public interface CompanyClient {

    /** I-07 업체 조회. 응답은 ApiResponse로 감싸지 않는다. */
    @GetMapping("/companies/{companyId}")
    CompanyResponse getCompany(@PathVariable("companyId") UUID companyId);

    /** I-08 상품 조회. */
    @GetMapping("/products/{productId}")
    ProductResponse getProduct(@PathVariable("productId") UUID productId);
}
