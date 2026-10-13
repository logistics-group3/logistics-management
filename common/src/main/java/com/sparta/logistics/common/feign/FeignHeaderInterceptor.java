package com.sparta.logistics.common.feign;

import com.sparta.logistics.common.auth.UserHeaders;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 서비스 간 Feign 호출 시 현재 요청의 X-User-* 헤더를 그대로 전달한다 (SA 6.3).
 * 하위 서비스도 같은 사용자 정보로 Audit 기록·권한 확인을 할 수 있다.
 */
public class FeignHeaderInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return; // 요청 스레드가 아닌 곳(스케줄러, @Async 등)에서의 호출
        }
        HttpServletRequest request = servletAttributes.getRequest();
        for (String header : UserHeaders.ALL) {
            String value = request.getHeader(header);
            if (value != null) {
                template.header(header, value);
            }
        }
    }
}
