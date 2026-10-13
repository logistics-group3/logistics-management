package com.sparta.logistics.user.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sparta.logistics.common.auth.UserRole;
import com.sparta.logistics.user.application.dto.UserInfoResult;
import java.util.UUID;

/**
 * I-01 내부 사용자 조회 응답.
 * 호출자: gateway(권한 재확인), company(업체 관리자 확인), delivery(배송담당자·소속 허브 확인)
 */
public record InternalUserResponse(
        UUID userId,
        String username,
        String name,
        String slackId,
        UserRole role,
        @JsonProperty("isDeliveryManager") boolean isDeliveryManager,
        UUID hubId
) {

    public static InternalUserResponse from(UserInfoResult result) {
        return new InternalUserResponse(result.userId(), result.username(), result.name(), result.slackId(),
                result.role(), result.deliveryManager(), result.hubId());
    }
}
