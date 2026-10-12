package com.sparta.logistics.user.presentation.dto;

import com.sparta.logistics.user.application.dto.TokenResult;

/** U-02 로그인 응답 data */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

    public static TokenResponse from(TokenResult result) {
        return new TokenResponse(result.accessToken(), "Bearer", result.expiresIn());
    }
}
