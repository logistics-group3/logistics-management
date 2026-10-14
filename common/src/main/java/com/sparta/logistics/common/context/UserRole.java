package com.sparta.logistics.common.context;

/**
 * 권한은 2단계. 배송담당자는 권한이 아니라 USER의 isDeliveryManager 속성이다.
 */
public enum UserRole {
    MASTER,
    USER
}
