package com.sparta.logistics.order.application;

import com.sparta.logistics.order.application.dto.CompanyResult;
import com.sparta.logistics.order.application.dto.ProductResult;
import java.util.UUID;

/** company 내부 API 조회 계약. 실제 Feign 어댑터는 infrastructure에서 구현한다. */
public interface CompanyReader {

    /** I-07. 없는 업체는 COMPANY_NOT_FOUND를 담은 BusinessException으로 전달한다. */
    CompanyResult getCompany(UUID companyId);

    /** I-08. 없는 상품은 PRODUCT_NOT_FOUND를 담은 BusinessException으로 전달한다. */
    ProductResult getProduct(UUID productId);
}
