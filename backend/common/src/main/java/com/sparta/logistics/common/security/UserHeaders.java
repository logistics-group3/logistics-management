package com.sparta.logistics.common.security;

import java.util.List;

/**
 * Gateway가 JWT 검증 후 주입하는 사용자 헤더 (SA 6.1). 서비스는 이 헤더만 신뢰한다.
 * 클라이언트가 같은 이름으로 보낸 헤더는 Gateway가 지운 뒤 다시 넣는다.
 */
public final class UserHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USERNAME = "X-Username";
    public static final String USER_ROLE = "X-User-Role";
    public static final String DELIVERY_MANAGER = "X-Delivery-Manager";

    /** Feign 호출 시 그대로 전달할 헤더 */
    public static final List<String> ALL = List.of(USER_ID, USERNAME, USER_ROLE, DELIVERY_MANAGER);

    private UserHeaders() {
    }
}
