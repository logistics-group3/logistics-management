package com.sparta.logistics.user.application;

import com.sparta.logistics.user.domain.User;

/** Access Token 발급 계약. 구현은 infrastructure/security/JwtTokenIssuer. */
public interface TokenIssuer {

    String issue(User user);

    /** 토큰 유효 시간(초) */
    long expiresInSeconds();
}
