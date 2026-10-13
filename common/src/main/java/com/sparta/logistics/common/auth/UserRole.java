package com.sparta.logistics.common.auth;

/** 권한 2단계 (SA 1.5 #3). 배송담당자는 role 이 아니라 USER 에게 부여하는 isDeliveryManager 속성이다. */
public enum UserRole {
    MASTER,
    USER
}
