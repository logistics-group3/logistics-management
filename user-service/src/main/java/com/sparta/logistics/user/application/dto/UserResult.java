package com.sparta.logistics.user.application.dto;

import com.sparta.logistics.common.context.UserRole;
import com.sparta.logistics.user.domain.AffiliationType;
import com.sparta.logistics.user.domain.User;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserResult(
        UUID userId,
        String username,
        String name,
        String slackId,
        AffiliationType affiliationType,
        String affiliationName,
        UserRole role,
        boolean deliveryManager,
        UUID hubId,
        LocalDateTime createdAt
) {

    public static UserResult from(User user) {
        return new UserResult(
                user.getId(),
                user.getUsername(),
                user.getName(),
                user.getSlackId(),
                user.getAffiliationType(),
                user.getAffiliationName(),
                user.getRole(),
                user.isDeliveryManager(),
                user.getHubId(),
                user.getCreatedAt()
        );
    }
}
