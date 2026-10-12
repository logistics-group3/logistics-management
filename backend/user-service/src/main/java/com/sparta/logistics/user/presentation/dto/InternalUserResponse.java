package com.sparta.logistics.user.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sparta.logistics.common.security.UserRole;
import com.sparta.logistics.user.application.dto.UserResult;
import java.util.UUID;

/** I-01 응답 (SA 6.3): userId, username, role, isDeliveryManager, slackId */
public record InternalUserResponse(
        UUID userId,
        String username,
        UserRole role,
        @JsonProperty("isDeliveryManager") boolean deliveryManager,
        String slackId
) {

    public static InternalUserResponse from(UserResult result) {
        return new InternalUserResponse(
                result.userId(), result.username(), result.role(), result.deliveryManager(), result.slackId());
    }
}
