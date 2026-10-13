package com.sparta.logistics.user.application.dto;

public record LoginResult(String accessToken, String tokenType, long expiresIn) {
}
