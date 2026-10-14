package com.sparta.logistics.common.feign;

import com.sparta.logistics.common.context.UserHeaders;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 서비스 간 Feign 호출 시 현재 요청의 X-User-* 헤더를 그대로 전파한다.
 * 하위 서비스의 Audit(created_by 등)과 로그 추적에 사용된다.
 */
public class FeignHeaderInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return;
        }
        HttpServletRequest request = attributes.getRequest();
        for (String header : UserHeaders.ALL) {
            String value = request.getHeader(header);
            if (value != null) {
                template.header(header, value);
            }
        }
    }
}
