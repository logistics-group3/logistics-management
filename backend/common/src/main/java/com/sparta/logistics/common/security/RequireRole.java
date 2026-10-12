package com.sparta.logistics.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 역할 확인 (SA 8.3 #7). 컨트롤러 메서드 또는 클래스에 붙이면 {@link RoleCheckInterceptor}가 확인한다.
 * 역할이 맞지 않으면 403 FORBIDDEN, 사용자 헤더가 없으면 401 UNAUTHORIZED.
 * <pre>
 * &#64;RequireRole(UserRole.MASTER)
 * &#64;PostMapping("/{hubId}/stocks/inbound")
 * </pre>
 * 소유자 검증("본인 주문인지")은 여기서 하지 않는다 → Application 계층에서 {@link UserContext}로 확인.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    UserRole[] value();
}
