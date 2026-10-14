package com.sparta.logistics.common.context;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class MasterOnlyInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod) || !isMasterOnly(handlerMethod)) {
            return true;
        }
        String role = request.getHeader(UserHeaders.USER_ROLE);
        if (role == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        if (!UserRole.MASTER.name().equals(role)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return true;
    }

    private boolean isMasterOnly(HandlerMethod handlerMethod) {
        return handlerMethod.hasMethodAnnotation(MasterOnly.class)
                || handlerMethod.getBeanType().isAnnotationPresent(MasterOnly.class);
    }
}
