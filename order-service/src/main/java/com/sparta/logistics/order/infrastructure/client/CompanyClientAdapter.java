package com.sparta.logistics.order.infrastructure.client;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.order.application.CompanyReader;
import com.sparta.logistics.order.application.dto.CompanyResult;
import com.sparta.logistics.order.application.dto.ProductResult;
import com.sparta.logistics.order.infrastructure.client.dto.CompanyResponse;
import com.sparta.logistics.order.infrastructure.client.dto.ProductResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CompanyClientAdapter implements CompanyReader {

    private final CompanyClient client;

    @Override
    public CompanyResult getCompany(UUID companyId) {
        CompanyResponse response = client.getCompany(companyId);
        if (response == null || !companyId.equals(response.companyId()) || response.hubId() == null
                || response.managerId() == null || !validText(response.name(), 100) || !validText(response.address(), 255)) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "업체 조회 응답이 계약과 다릅니다.");
        }
        return new CompanyResult(response.companyId(), response.name(), response.hubId(), response.address(), response.managerId());
    }

    @Override
    public ProductResult getProduct(UUID productId) {
        ProductResponse response = client.getProduct(productId);
        // Note: 전송 DTO의 Long은 누락과 0원을 구분한다. 누락된 단가를 기본값 0으로 처리하면 무료 주문이 만들어진다.
        if (response == null || !productId.equals(response.productId()) || response.companyId() == null
                || !validText(response.name(), 100) || response.price() == null || response.price() < 0) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "상품 조회 응답이 계약과 다릅니다.");
        }
        return new ProductResult(response.productId(), response.companyId(), response.name(), response.price());
    }

    private static boolean validText(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength;
    }
}
