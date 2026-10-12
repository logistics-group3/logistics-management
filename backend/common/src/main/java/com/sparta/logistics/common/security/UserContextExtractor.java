package com.sparta.logistics.common.security;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * 헤더 → {@link UserContext} 변환 (ArgumentResolver·RoleCheckInterceptor 공용).
 */
final class UserContextExtractor {

    private UserContextExtractor() {
    }

    /** @param header 헤더 이름 → 값 (없으면 null) */
    static UserContext extract(UnaryOperator<String> header) {
        String userId = header.apply(UserHeaders.USER_ID);
        String role = header.apply(UserHeaders.USER_ROLE);
        if (userId == null || role == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        try {
            return new UserContext(
                    UUID.fromString(userId),
                    header.apply(UserHeaders.USERNAME),
                    UserRole.valueOf(role),
                    Boolean.parseBoolean(header.apply(UserHeaders.DELIVERY_MANAGER))
            );
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
    }
}
