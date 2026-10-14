package com.sparta.logistics.common.context;

import java.util.UUID;

/**
 * 현재 요청 사용자. 컨트롤러 파라미터에 {@code @CurrentUser UserContext user}로 받는다.
 */
public record UserContext(UUID userId, String username, UserRole role, boolean deliveryManager) {

    public boolean isMaster() {
        return role == UserRole.MASTER;
    }

    public boolean isSelf(UUID targetUserId) {
        return userId.equals(targetUserId);
    }
}
