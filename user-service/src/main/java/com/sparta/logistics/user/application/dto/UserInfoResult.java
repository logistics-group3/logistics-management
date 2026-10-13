package com.sparta.logistics.user.application.dto;

import com.sparta.logistics.common.auth.UserRole;
import com.sparta.logistics.user.domain.User;
import java.util.UUID;

public record UserInfoResult(
        UUID userId,
        String username,
        String name,
        String slackId,
        UserRole role,
        boolean deliveryManager,
        UUID hubId
) {

    public static UserInfoResult from(User user) {
        return new UserInfoResult(user.getId(), user.getUsername(), user.getName(), user.getSlackId(),
                user.getRole(), user.isDeliveryManager(), user.getHubId());
    }
}
