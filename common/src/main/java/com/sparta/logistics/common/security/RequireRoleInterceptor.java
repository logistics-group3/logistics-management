package com.sparta.logistics.common.security;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** @RequireRole을 확인한다. UserContextInterceptor보다 뒤에 등록해야 한다. */
public class RequireRoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequireRole requireRole = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(), RequireRole.class);
        if (requireRole == null) {
            requireRole = AnnotatedElementUtils.findMergedAnnotation(
                    handlerMethod.getBeanType(), RequireRole.class);
        }
        if (requireRole == null) {
            return true;
        }

        UserContext user = UserContextHolder.get()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (Arrays.stream(requireRole.value()).noneMatch(role -> role == user.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return true;
    }
}
