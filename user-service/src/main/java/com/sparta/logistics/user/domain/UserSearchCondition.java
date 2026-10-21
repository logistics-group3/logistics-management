package com.sparta.logistics.user.domain;

import com.sparta.logistics.common.auth.Role;
import java.util.UUID;

/**
 * U-05 검색 조건. 값이 null이면 그 조건을 쓰지 않는다.
 * domain의 UserRepository.search가 받는 타입이라 application이 아닌 domain에 둔다(domain은 다른 계층을 모른다).
 */
public record UserSearchCondition(
        String username,
        String name,
        Role role,
        Boolean isDeliveryManager,
        UUID hubId
) {
}
