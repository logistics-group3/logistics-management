package com.sparta.logistics.common.context;

import java.util.List;

/**
 * Gateway가 JWT 검증 후 주입하는 사용자 헤더 이름. 하위 서비스는 이 헤더만 신뢰한다.
 */
public final class UserHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USERNAME = "X-Username";
    public static final String USER_ROLE = "X-User-Role";
    public static final String DELIVERY_MANAGER = "X-Delivery-Manager";

    public static final List<String> ALL = List.of(USER_ID, USERNAME, USER_ROLE, DELIVERY_MANAGER);

    private UserHeaders() {
    }
}
