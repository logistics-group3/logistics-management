package com.sparta.logistics.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 붙이면 X-User-* 헤더를 {@link UserContext}로 주입한다. 헤더가 없으면 401 UNAUTHORIZED.
 * <pre>
 * public ResponseEntity&lt;...&gt; create(&#64;CurrentUser UserContext user, &#64;Valid &#64;RequestBody ...)
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
