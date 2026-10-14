package com.sparta.logistics.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.web.servlet.HandlerInterceptor;

/** X-User-* 헤더를 UserContext로 바꿔 ThreadLocal에 넣는다. Public API처럼 헤더가 없으면 비워 둔다. */
public class UserContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userId = request.getHeader(UserHeaders.USER_ID);
        String role = request.getHeader(UserHeaders.USER_ROLE);
        if (userId == null || role == null) {
            return true;
        }
        try {
            UserContextHolder.set(new UserContext(
                    UUID.fromString(userId),
                    request.getHeader(UserHeaders.USERNAME),
                    Role.valueOf(role),
                    Boolean.parseBoolean(request.getHeader(UserHeaders.DELIVERY_MANAGER))));
        } catch (IllegalArgumentException e) {
            // 형식이 잘못된 헤더는 없는 것으로 취급한다. 보호된 API는 이후 401로 막힌다.
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        UserContextHolder.clear();
    }
}
