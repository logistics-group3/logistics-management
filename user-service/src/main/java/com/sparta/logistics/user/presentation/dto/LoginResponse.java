package com.sparta.logistics.user.presentation.dto;

import com.sparta.logistics.user.application.dto.LoginResult;

public record LoginResponse(String accessToken, String tokenType, long expiresIn) {

    public static LoginResponse from(LoginResult result) {
        return new LoginResponse(result.accessToken(), result.tokenType(), result.expiresIn());
    }
}
