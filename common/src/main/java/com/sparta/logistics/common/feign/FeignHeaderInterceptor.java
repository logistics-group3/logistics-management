package com.sparta.logistics.common.feign;

import com.sparta.logistics.common.security.UserContext;
import com.sparta.logistics.common.security.UserContextHolder;
import com.sparta.logistics.common.security.UserHeaders;
import feign.RequestInterceptor;
import feign.RequestTemplate;

/** 현재 사용자의 X-User-* 헤더를 다음 호출에 그대로 전달한다. 하위 서비스도 감사 컬럼을 채울 수 있다 (ADR-04). */
public class FeignHeaderInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        UserContext user = UserContextHolder.get().orElse(null);
        if (user == null) {
            return;
        }
        template.header(UserHeaders.USER_ID, user.userId().toString());
        template.header(UserHeaders.USER_ROLE, user.role().name());
        template.header(UserHeaders.DELIVERY_MANAGER, String.valueOf(user.deliveryManager()));
        if (user.username() != null) {
            template.header(UserHeaders.USERNAME, user.username());
        }
    }
}
