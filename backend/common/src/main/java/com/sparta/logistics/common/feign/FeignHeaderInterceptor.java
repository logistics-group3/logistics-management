package com.sparta.logistics.common.feign;

import com.sparta.logistics.common.security.UserHeaders;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 서비스 간 Feign 호출 시 현재 요청의 X-User-* 헤더를 그대로 전달한다 (SA 6.3).
 * 호출받은 서비스도 같은 사용자로 Audit(created_by)을 기록하고 권한을 판단할 수 있다.
 * 요청 스레드 밖(@Async 등)에서 호출하면 전달할 헤더가 없으므로 아무것도 붙이지 않는다.
 */
public class FeignHeaderInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return;
        }
        for (String name : UserHeaders.ALL) {
            String value = attributes.getRequest().getHeader(name);
            if (value != null) {
                template.header(name, value);
            }
        }
    }
}
