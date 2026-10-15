package com.sparta.logistics.hub.application.dto;

import com.sparta.logistics.hub.domain.Hub;
import java.util.UUID;

/** I-02 응답. company·user가 허브 존재 확인과 허브명 조회에 쓴다. */
public record InternalHubResult(
        UUID hubId,
        String name,
        String address
) {

    public static InternalHubResult from(Hub hub) {
        return new InternalHubResult(hub.getId(), hub.getName(), hub.getAddress());
    }
}
