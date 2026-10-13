package com.sparta.logistics.common.auth;

/**
 * Gateway가 인증 후 주입하는 사용자 헤더 이름. SA 6.1. gateway-service에도 같은 값이 있다.
 */
public final class UserHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USERNAME = "X-Username";
    public static final String ROLE = "X-User-Role";
    public static final String DELIVERY_MANAGER = "X-Delivery-Manager";

    public static final String[] ALL = {USER_ID, USERNAME, ROLE, DELIVERY_MANAGER};

    private UserHeaders() {
    }
}
