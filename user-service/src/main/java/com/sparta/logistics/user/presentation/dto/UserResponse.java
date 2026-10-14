package com.sparta.logistics.user.presentation.dto;

import com.sparta.logistics.common.context.UserRole;
import com.sparta.logistics.user.application.dto.UserResult;
import com.sparta.logistics.user.domain.AffiliationType;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID userId,
        String username,
        String name,
        String slackId,
        AffiliationType affiliationType,
        String affiliationName,
        UserRole role,
        boolean isDeliveryManager,
        UUID hubId,
        LocalDateTime createdAt
) {

    public static UserResponse from(UserResult result) {
        return new UserResponse(
                result.userId(),
                result.username(),
                result.name(),
                result.slackId(),
                result.affiliationType(),
                result.affiliationName(),
                result.role(),
                result.deliveryManager(),
                result.hubId(),
                result.createdAt()
        );
    }
}
