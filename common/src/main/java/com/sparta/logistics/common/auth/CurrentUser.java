package com.sparta.logistics.common.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 현재 사용자 정보를 주입한다.
 * <pre>
 * &#64;GetMapping("/me")
 * public ApiResponse&lt;...&gt; me(&#64;CurrentUser UserContext user) { ... }
 * </pre>
 * 헤더가 없으면 401 UNAUTHORIZED. Public API 처럼 없을 수도 있으면 {@code @CurrentUser(required = false)} → null.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {

    boolean required() default true;
}
