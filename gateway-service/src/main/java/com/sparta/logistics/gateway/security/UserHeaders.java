package com.sparta.logistics.gateway.security;

import java.util.List;

/** 서비스로 전달하는 사용자 헤더. common 모듈의 UserHeaders와 같은 값이다(gateway는 MVC 기반 common에 의존하지 않는다). */
public final class UserHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USERNAME = "X-Username";
    public static final String ROLE = "X-User-Role";
    public static final String DELIVERY_MANAGER = "X-Delivery-Manager";

    public static final List<String> ALL = List.of(USER_ID, USERNAME, ROLE, DELIVERY_MANAGER);

    private UserHeaders() {
    }
}
