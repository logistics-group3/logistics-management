package com.sparta.logistics.common.context;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && UserContext.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public UserContext resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                       NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String userId = webRequest.getHeader(UserHeaders.USER_ID);
        String role = webRequest.getHeader(UserHeaders.USER_ROLE);
        if (userId == null || role == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        try {
            return new UserContext(
                    UUID.fromString(userId),
                    webRequest.getHeader(UserHeaders.USERNAME),
                    UserRole.valueOf(role),
                    Boolean.parseBoolean(webRequest.getHeader(UserHeaders.DELIVERY_MANAGER))
            );
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
    }
}
