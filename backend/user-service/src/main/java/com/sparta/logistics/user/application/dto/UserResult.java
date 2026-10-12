package com.sparta.logistics.user.application.dto;

import com.sparta.logistics.common.security.UserRole;
import com.sparta.logistics.user.domain.model.AffiliationType;
import com.sparta.logistics.user.domain.model.User;
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
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static UserResult from(User user) {
        return new UserResult(
                user.getUserId(),
                user.getUsername(),
                user.getName(),
                user.getSlackId(),
                user.getAffiliationType(),
                user.getAffiliationName(),
                user.getRole(),
                user.isDeliveryManager(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
