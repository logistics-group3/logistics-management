package com.sparta.logistics.common.security;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * {@link RequireRole}이 붙은 핸들러의 역할을 확인한다. 메서드에 붙은 값이 클래스 값보다 우선한다.
 * 여기서 던진 예외도 GlobalExceptionHandler가 공통 에러 형식으로 바꾼다.
 */
public class RoleCheckInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RequireRole required = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RequireRole.class);
        if (required == null) {
            required = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequireRole.class);
        }
        if (required == null) {
            return true;
        }
        UserContext user = UserContextExtractor.extract(request::getHeader);
        if (Arrays.stream(required.value()).noneMatch(role -> role == user.role())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return true;
    }
}
