package com.sparta.logistics.common.web;

import com.sparta.logistics.common.auth.RequireRole;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.auth.UserContextExtractor;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** {@link RequireRole} 이 붙은 핸들러의 역할을 확인한다. 예외는 GlobalExceptionHandler 가 401/403 으로 변환한다. */
public class RoleCheckInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (requireRole == null) {
            return true;
        }

        UserContext user = UserContextExtractor.extract(request::getHeader);
        if (user == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        boolean allowed = Arrays.asList(requireRole.value()).contains(user.role());
        if (!allowed) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return true;
    }
}
