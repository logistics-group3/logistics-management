package com.sparta.logistics.hub.presentation.dto;

import com.sparta.logistics.hub.application.dto.HubResult;
import java.math.BigDecimal;
import java.util.UUID;

/** H-01, H-02 응답(SA 6.5 HubResponse). */
public record HubResponse(
        UUID hubId,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude
) {

    public static HubResponse from(HubResult result) {
        return new HubResponse(result.hubId(), result.name(), result.address(), result.latitude(), result.longitude());
    }
}
