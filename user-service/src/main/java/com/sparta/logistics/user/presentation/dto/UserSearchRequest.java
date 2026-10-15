package com.sparta.logistics.user.presentation.dto;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.user.domain.UserSearchCondition;
import java.util.UUID;

/** U-05 쿼리 파라미터. 모두 선택이고 page, size, sort는 공통 Pageable 규칙을 따른다. */
public record UserSearchRequest(
        String username,
        String name,
        Role role,
        Boolean isDeliveryManager,
        UUID hubId
) {

    public UserSearchCondition toCondition() {
        return new UserSearchCondition(username, name, role, isDeliveryManager, hubId);
    }
}
