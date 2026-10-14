package com.sparta.logistics.user.application.dto;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.user.domain.User;
import java.util.UUID;

/** I-01 응답. Gateway 권한 재확인과 다른 서비스의 사용자 확인에 쓴다. */
public record InternalUserResult(
        UUID userId,
        String username,
        Role role,
        boolean isDeliveryManager,
        UUID hubId,
        String slackId
) {

    public static InternalUserResult from(User user) {
        return new InternalUserResult(user.getId(), user.getUsername(), user.getRole(),
                user.isDeliveryManager(), user.getHubId(), user.getSlackId());
    }
}
