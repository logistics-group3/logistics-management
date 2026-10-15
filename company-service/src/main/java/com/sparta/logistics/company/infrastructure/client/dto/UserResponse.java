package com.sparta.logistics.company.infrastructure.client.dto;

import java.util.UUID;

/** I-01 응답 중 company가 쓰는 필드. 존재 확인만 하므로 식별자만 받는다. */
public record UserResponse(
        UUID userId
) {
}
