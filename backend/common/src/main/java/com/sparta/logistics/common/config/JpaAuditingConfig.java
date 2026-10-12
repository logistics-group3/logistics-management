package com.sparta.logistics.common.config;

import com.sparta.logistics.common.security.UserHeaders;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    /** 헤더가 없는 작업(회원가입, 기동 시 초기화, 비동기 작업)은 SYSTEM으로 기록한다. */
    public static final String SYSTEM = "SYSTEM";

    /** created_by / updated_by = X-Username 헤더 (SA 5.1) */
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                String username = attributes.getRequest().getHeader(UserHeaders.USERNAME);
                if (StringUtils.hasText(username)) {
                    return Optional.of(username);
                }
            }
            return Optional.of(SYSTEM);
        };
    }
}
