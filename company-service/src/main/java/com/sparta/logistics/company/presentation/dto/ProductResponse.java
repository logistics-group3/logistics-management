package com.sparta.logistics.company.presentation.dto;

import com.sparta.logistics.company.application.dto.ProductResult;
import java.time.LocalDateTime;
import java.util.UUID;

/** SA 6.6 ProductResponse. hubId는 소속 업체의 관리 허브다. */
public record ProductResponse(
        UUID productId,
        UUID companyId,
        UUID hubId,
        String name,
        String description,
        Long price,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ProductResponse from(ProductResult result) {
        return new ProductResponse(result.productId(), result.companyId(), result.hubId(), result.name(),
                result.description(), result.price(), result.createdAt(), result.updatedAt());
    }
}
