package com.sparta.logistics.user.application.dto;

import com.sparta.logistics.common.auth.Role;
import java.util.UUID;

/** U-07 입력. null인 값은 바꾸지 않는다. 값 조합 검증은 ChangeRoleRequest가 먼저 한다. */
public record ChangeRoleCommand(Role role, Boolean isDeliveryManager, UUID hubId) {
}
