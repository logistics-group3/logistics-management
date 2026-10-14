package com.sparta.logistics.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 API를 부를 수 있는 역할. 클래스·메서드에 선언하며 메서드가 우선한다 (ADR-05).
 * 소유자 확인("내 업체의 상품인가")은 데이터가 필요하므로 Application 계층에서 한다.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    Role[] value();
}
