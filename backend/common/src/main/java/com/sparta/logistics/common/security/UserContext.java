package com.sparta.logistics.common.security;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import java.util.UUID;

/**
 * 현재 요청 사용자 (Gateway가 넣은 X-User-* 헤더). 컨트롤러에서 {@code @CurrentUser UserContext user}로 받는다.
 * 소유자 검증(업체 manager_id, 주문 orderer_id, 배송 manager_id)은 이 값을 받아 각 서비스 Application/Entity에서 한다.
 */
public record UserContext(UUID userId, String username, UserRole role, boolean deliveryManager) {

    public boolean isMaster() {
        return role == UserRole.MASTER;
    }

    public boolean isDeliveryManager() {
        return deliveryManager;
    }

    public boolean isSelf(UUID otherUserId) {
        return userId.equals(otherUserId);
    }

    /** 본인 또는 MASTER */
    public boolean isSelfOrMaster(UUID ownerUserId) {
        return isMaster() || isSelf(ownerUserId);
    }

    public void validateMaster() {
        if (!isMaster()) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    public void validateSelfOrMaster(UUID ownerUserId) {
        if (!isSelfOrMaster(ownerUserId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }
}
