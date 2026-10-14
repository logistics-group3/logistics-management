package com.sparta.logistics.gateway.constant;

/** common을 쓰지 않으므로(WebFlux) 헤더 이름을 gateway에도 따로 둔다. common의 UserHeaders와 같은 값이어야 한다. */
public final class UserHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USERNAME = "X-Username";
    public static final String USER_ROLE = "X-User-Role";
    public static final String DELIVERY_MANAGER = "X-Delivery-Manager";

    private UserHeaders() {
    }
}
