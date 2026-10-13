package com.sparta.logistics.user.application.dto;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.user.domain.User;
import java.util.UUID;

public record SignupResult(UUID userId, String username, Role role, boolean isDeliveryManager) {

    public static SignupResult from(User user) {
        return new SignupResult(user.getId(), user.getUsername(), user.getRole(), user.isDeliveryManager());
    }
}
