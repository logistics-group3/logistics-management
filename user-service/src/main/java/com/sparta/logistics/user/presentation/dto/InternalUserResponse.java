package com.sparta.logistics.user.presentation.dto;

import com.sparta.logistics.common.context.UserRole;
import com.sparta.logistics.user.application.dto.UserResult;
import java.util.UUID;

/** I-01 응답 (호출자: gateway, company, delivery). 내부 API라 ApiResponse로 감싸지 않는다. */
public record InternalUserResponse(
        UUID userId,
        String username,
        UserRole role,
        boolean isDeliveryManager,
        UUID hubId,
        String slackId
) {

    public static InternalUserResponse from(UserResult result) {
        return new InternalUserResponse(
                result.userId(),
                result.username(),
                result.role(),
                result.deliveryManager(),
                result.hubId(),
                result.slackId()
        );
    }
}
