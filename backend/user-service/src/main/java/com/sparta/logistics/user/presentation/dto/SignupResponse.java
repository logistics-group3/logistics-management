package com.sparta.logistics.user.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sparta.logistics.common.security.UserRole;
import com.sparta.logistics.user.application.dto.UserResult;
import java.util.UUID;

/** U-01 회원가입 응답 data */
public record SignupResponse(
        UUID userId,
        String username,
        UserRole role,
        @JsonProperty("isDeliveryManager") boolean deliveryManager
) {

    public static SignupResponse from(UserResult result) {
        return new SignupResponse(result.userId(), result.username(), result.role(), result.deliveryManager());
    }
}
