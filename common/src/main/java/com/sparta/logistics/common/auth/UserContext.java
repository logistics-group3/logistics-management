package com.sparta.logistics.common.auth;

import java.util.UUID;

/**
 * Gateway가 검증한 현재 요청의 사용자. 컨트롤러에서 {@code @CurrentUser UserContext user}로 받는다.
 */
public record UserContext(UUID userId, String username, Role role, boolean deliveryManager) {

    public boolean isMaster() {
        return role == Role.MASTER;
    }
}
