package com.sparta.logistics.common.auth;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import java.util.UUID;
import java.util.function.Function;

/** 헤더 → UserContext 변환. 헤더 이름으로 값을 꺼내는 함수만 받아서 MVC/Feign 어디서든 쓸 수 있게 했다. */
public final class UserContextExtractor {

    private UserContextExtractor() {
    }

    /** @return 사용자 헤더가 전혀 없으면 null */
    public static UserContext extract(Function<String, String> headerReader) {
        String userId = headerReader.apply(UserHeaders.USER_ID);
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return new UserContext(
                    UUID.fromString(userId),
                    headerReader.apply(UserHeaders.USERNAME),
                    UserRole.valueOf(headerReader.apply(UserHeaders.USER_ROLE)),
                    Boolean.parseBoolean(headerReader.apply(UserHeaders.DELIVERY_MANAGER))
            );
        } catch (IllegalArgumentException | NullPointerException e) {
            // Gateway 를 거쳤다면 생길 수 없는 상황 (형식이 깨진 헤더)
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
    }
}
