package com.sparta.logistics.company.application.dto;

import com.sparta.logistics.company.domain.Product;
import java.time.LocalDateTime;
import java.util.UUID;

/** P-01~P-04 출력(SA 6.6 ProductResponse). hubId는 상품에 저장하지 않고 소속 업체에서 가져온다. */
public record ProductResult(
        UUID productId,
        UUID companyId,
        UUID hubId,
        String name,
        String description,
        Long price,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ProductResult from(Product product) {
        return new ProductResult(product.getId(), product.getCompany().getId(), product.getCompany().getHubId(),
                product.getName(), product.getDescription(), product.getPrice(),
                product.getCreatedAt(), product.getUpdatedAt());
    }
}
