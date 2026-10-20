package com.sparta.logistics.hub.infrastructure.client;

import com.sparta.logistics.common.feign.IdempotentRetryConfig;
import com.sparta.logistics.hub.infrastructure.client.dto.CompanyResponse;
import com.sparta.logistics.hub.infrastructure.client.dto.ProductResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// Note: GET이라 응답을 받지 못한 실패(연결 실패·타임아웃)는 IdempotentRetryConfig가 최대 3회 다시 시도한다(SA 3.5).
// Note: company-service 설정(타임아웃)은 클라이언트 이름 `company-service`로 걸려 있다. contextId를 따로 주면 적용되지 않으므로 한 인터페이스에 모은다.
@FeignClient(name = "company-service", path = "/internal", configuration = IdempotentRetryConfig.class)
public interface ProductClient {

    /** I-08 상품 조회 (제공: company-service) */
    @GetMapping("/products/{productId}")
    ProductResponse getProduct(@PathVariable UUID productId);

    /** I-07 업체 조회 (제공: company-service). 입고 권한 확인에 쓴다. */
    @GetMapping("/companies/{companyId}")
    CompanyResponse getCompany(@PathVariable UUID companyId);
}
