package com.sparta.logistics.common.jpa;

import com.sparta.logistics.common.context.UserHeaders;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.AuditorAware;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 현재 요청의 X-User-Id 헤더를 Audit 작성자로 사용한다. 헤더가 없으면(회원가입, 비동기 작업 등) 비워 둔다.
 */
public class HeaderAuditorAware implements AuditorAware<UUID> {

    @Override
    public Optional<UUID> getCurrentAuditor() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return Optional.empty();
        }
        String userId = attributes.getRequest().getHeader(UserHeaders.USER_ID);
        if (userId == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(userId));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
