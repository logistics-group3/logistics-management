package com.sparta.logistics.common.security;

import java.util.UUID;

/** X-User-* 헤더로 만든 현재 사용자. */
public record UserContext(UUID userId, String username, Role role, boolean deliveryManager) {

    public boolean isMaster() {
        return role == Role.MASTER;
    }

    public boolean isOwnerOrMaster(UUID ownerId) {
        return isMaster() || userId.equals(ownerId);
    }
}
