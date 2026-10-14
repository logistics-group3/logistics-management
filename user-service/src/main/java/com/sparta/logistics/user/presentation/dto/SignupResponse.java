package com.sparta.logistics.user.presentation.dto;

import com.sparta.logistics.common.context.UserRole;
import com.sparta.logistics.user.application.dto.UserResult;
import java.util.UUID;

public record SignupResponse(UUID userId, String username, UserRole role, boolean isDeliveryManager) {

    public static SignupResponse from(UserResult result) {
        return new SignupResponse(result.userId(), result.username(), result.role(), result.deliveryManager());
    }
}
