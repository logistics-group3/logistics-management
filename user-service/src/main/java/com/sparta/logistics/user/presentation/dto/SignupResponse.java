package com.sparta.logistics.user.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sparta.logistics.common.auth.UserRole;
import com.sparta.logistics.user.application.dto.SignupResult;
import java.util.UUID;

public record SignupResponse(UUID userId, String username, UserRole role, @JsonProperty("isDeliveryManager") boolean isDeliveryManager) {

    public static SignupResponse from(SignupResult result) {
        return new SignupResponse(result.userId(), result.username(), result.role(), result.deliveryManager());
    }
}
