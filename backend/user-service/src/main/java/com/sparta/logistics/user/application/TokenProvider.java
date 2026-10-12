package com.sparta.logistics.user.application;

import com.sparta.logistics.common.security.UserRole;
import java.util.UUID;

/** 토큰 발급 포트. 구현: infrastructure/jwt/JwtTokenProvider */
public interface TokenProvider {

    String createAccessToken(UUID userId, String username, UserRole role);

    long getExpirationSeconds();
}
