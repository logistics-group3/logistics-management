package com.sparta.logistics.common.security;

/** Gateway가 검증 후 넣어주는 헤더 (API 설계서 '전달 헤더'). */
public final class UserHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USERNAME = "X-Username";
    public static final String USER_ROLE = "X-User-Role";
    public static final String DELIVERY_MANAGER = "X-Delivery-Manager";

    /** 헤더가 없는 시스템 작업의 감사 컬럼 값. */
    public static final String SYSTEM_USER = "SYSTEM";

    private UserHeaders() {
    }
}
