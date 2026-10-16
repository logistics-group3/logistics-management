package com.sparta.logistics.user.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.user.application.dto.UserResult;
import com.sparta.logistics.user.domain.AffiliationType;
import java.time.LocalDateTime;
import java.util.UUID;

/** SA 6.4 UserResponse. U-03~U-07 응답에 공통으로 쓴다. */
public record UserResponse(
        UUID userId,
        String username,
        String name,
        String slackId,
        AffiliationType affiliationType,
        String affiliationName,
        Role role,
        boolean isDeliveryManager,
        UUID hubId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String slackEmail
) {

    public static UserResponse from(UserResult result) {
        return new UserResponse(result.userId(), result.username(), result.name(), result.slackId(),
                result.affiliationType(), result.affiliationName(), result.role(), result.isDeliveryManager(),
                result.hubId(), result.createdAt(), result.updatedAt(), result.slackEmail());
    }
}
