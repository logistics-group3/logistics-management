package com.sparta.logistics.company.application.dto;

import com.sparta.logistics.company.domain.Product;
import java.util.UUID;

/** I-08 응답. order가 상품이 공급업체 소속인지 확인하고 상품명·단가 스냅샷을 저장한다(SA 6.3). */
public record InternalProductResult(
        UUID productId,
        UUID companyId,
        String name,
        Long price
) {

    public static InternalProductResult from(Product product) {
        return new InternalProductResult(product.getId(), product.getCompany().getId(),
                product.getName(), product.getPrice());
    }
}
