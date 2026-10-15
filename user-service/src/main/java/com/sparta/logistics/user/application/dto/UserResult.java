package com.sparta.logistics.user.application.dto;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.user.domain.AffiliationType;
import com.sparta.logistics.user.domain.User;
import java.time.LocalDateTime;
import java.util.UUID;

/** U-03~U-07 공통 사용자 정보(SA 6.4 UserResponse). 비밀번호 해시는 담지 않는다. */
public record UserResult(
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
        LocalDateTime updatedAt
) {

    public static UserResult from(User user) {
        return new UserResult(user.getId(), user.getUsername(), user.getName(), user.getSlackId(),
                user.getAffiliationType(), user.getAffiliationName(), user.getRole(), user.isDeliveryManager(),
                user.getHubId(), user.getCreatedAt(), user.getUpdatedAt());
    }
}
