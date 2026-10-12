package com.sparta.logistics.common.security;

/**
 * 권한 2단계 (SA 1.5 #3). 배송담당자는 role이 아니라 USER에게 부여하는 is_delivery_manager 속성이다.
 */
public enum UserRole {
    MASTER,
    USER
}
