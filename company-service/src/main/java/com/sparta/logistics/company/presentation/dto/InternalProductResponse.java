package com.sparta.logistics.company.presentation.dto;

import com.sparta.logistics.company.application.dto.InternalProductResult;
import java.util.UUID;

/** I-08 응답(SA 6.3). 호출자는 order다. */
public record InternalProductResponse(
        UUID productId,
        UUID companyId,
        String name,
        Long price
) {

    public static InternalProductResponse from(InternalProductResult result) {
        return new InternalProductResponse(result.productId(), result.companyId(), result.name(), result.price());
    }
}
