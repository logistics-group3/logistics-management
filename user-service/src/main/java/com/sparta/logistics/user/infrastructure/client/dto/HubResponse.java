package com.sparta.logistics.user.infrastructure.client.dto;

import java.util.UUID;

/** I-02 응답(hubId, name, address) 중 user가 쓰는 필드만 둔다. 모르는 필드는 Jackson이 무시한다. */
public record HubResponse(UUID hubId, String name) {
}
