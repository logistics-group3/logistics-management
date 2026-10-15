package com.sparta.logistics.company.infrastructure.client.dto;

import java.util.UUID;

/** I-02 응답 중 company가 쓰는 필드(SA 6.3) */
public record HubResponse(
        UUID hubId,
        String name,
        String address
) {
}
