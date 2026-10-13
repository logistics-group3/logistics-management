package com.sparta.logistics.common.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 메서드나 클래스에 붙여 허용 역할을 제한한다. 예: {@code @RequireRole(Role.MASTER)}.
 * 역할 확인만 한다. 소유자 검증(업체 관리자, 주문자)은 application 계층에서 한다(SA 8.3).
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    Role[] value();
}
