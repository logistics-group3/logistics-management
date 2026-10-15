package com.sparta.logistics.user.presentation.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.user.application.dto.ChangeRoleCommand;
import jakarta.validation.constraints.AssertTrue;
import java.util.UUID;

/**
 * U-07 요청. 보낸 필드만 바꾼다. 사용자 상태 없이 판단할 수 있는 조합은 여기서 400 VALIDATION_ERROR로 거절한다.
 * 사용자 상태가 필요한 조합(배송담당자가 아닌데 hubId만 보냄)은 UserService가 거절한다.
 */
public record ChangeRoleRequest(
        Role role,
        Boolean isDeliveryManager,
        UUID hubId
) {

    /** 배송담당자로 지정하면 소속 허브가 필수다(SA U-07). */
    @JsonIgnore
    @AssertTrue(message = "배송담당자로 지정하려면 hubId가 필요합니다.")
    public boolean isHubIdPresentWhenAssigning() {
        return !Boolean.TRUE.equals(isDeliveryManager) || hubId != null;
    }

    /** 해제하면서 허브를 함께 보내는 요청은 의도가 모호하다(user 결정 0001). */
    @JsonIgnore
    @AssertTrue(message = "배송담당자를 해제할 때는 hubId를 보낼 수 없습니다.")
    public boolean isHubIdAbsentWhenReleasing() {
        return !Boolean.FALSE.equals(isDeliveryManager) || hubId == null;
    }

    public ChangeRoleCommand toCommand() {
        return new ChangeRoleCommand(role, isDeliveryManager, hubId);
    }
}
