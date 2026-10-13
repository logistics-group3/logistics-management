package com.sparta.logistics.common.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.UUID;

/**
 * Gateway 가 넣어준 X-User-* 헤더로 만든 현재 사용자 정보.
 * 컨트롤러에서 {@code @CurrentUser UserContext user} 로 받는다.
 */
public record UserContext(
        UUID userId,
        String username,
        UserRole role,
        boolean deliveryManager
) {

    @JsonIgnore
    public boolean isMaster() {
        return role == UserRole.MASTER;
    }

    public boolean isSelf(UUID targetUserId) {
        return userId.equals(targetUserId);
    }
}
