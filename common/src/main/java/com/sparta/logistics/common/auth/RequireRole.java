package com.sparta.logistics.common.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 역할 체크 (SA 8.3). 메서드 또는 컨트롤러 클래스에 붙인다. 메서드가 우선.
 * <pre>&#64;RequireRole(UserRole.MASTER)</pre>
 * 헤더 없음 → 401 UNAUTHORIZED, 역할 불일치 → 403 FORBIDDEN.
 * 소유자 검증(업체 manager_id, 주문 orderer_id 등)은 여기서 하지 않고 Application 계층에서 한다.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    UserRole[] value();
}
