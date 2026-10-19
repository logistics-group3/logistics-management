package com.sparta.logistics.common.auth;

import java.util.UUID;

/**
 * Gateway 권한 재확인 결과(I-01)를 Redis에 둘 때의 키 규칙. gateway가 저장하고 user-service가 권한 변경·탈퇴 때 지운다.
 * Note: 저장하는 쪽과 지우는 쪽이 다른 서비스라 키 형식이 어긋나면 삭제가 조용히 실패한다. 그래서 한 곳(common-core)에 둔다.
 */
public final class UserAuthCacheKeys {

    public static final String PREFIX = "auth:user:";

    private UserAuthCacheKeys() {
    }

    public static String of(UUID userId) {
        return PREFIX + userId;
    }
}
