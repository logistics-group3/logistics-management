package com.sparta.logistics.common.context;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * MASTER 전용 API. 컨트롤러 메서드나 클래스에 붙이면 MASTER가 아닌 요청은 403 FORBIDDEN.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface MasterOnly {
}
