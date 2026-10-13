package com.sparta.logistics.common.jpa;

import com.sparta.logistics.common.auth.UserHeaders;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * created_by / updated_by 값을 Gateway 가 넣어준 X-Username 헤더로 채운다 (SA 5.1).
 * 헤더가 없는 경우(회원가입 같은 Public API, 시드 데이터, 스케줄러 등)는 SYSTEM.
 */
public class HeaderAuditorAware implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            String username = request.getHeader(UserHeaders.USERNAME);
            if (username != null && !username.isBlank()) {
                return Optional.of(username);
            }
        }
        return Optional.of(UserHeaders.SYSTEM);
    }
}
