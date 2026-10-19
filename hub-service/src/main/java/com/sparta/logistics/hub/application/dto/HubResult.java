package com.sparta.logistics.hub.application.dto;

import com.sparta.logistics.hub.domain.Hub;
import java.math.BigDecimal;
import java.util.UUID;

/** H-01, H-02 조회 결과. 응답 형식은 presentation의 HubResponse가 정한다. */
public record HubResult(
        UUID hubId,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude
) {

    public static HubResult from(Hub hub) {
        return new HubResult(hub.getId(), hub.getName(), hub.getAddress(), hub.getLatitude(), hub.getLongitude());
    }
}
