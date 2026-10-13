package com.sparta.logistics.common.config;

import com.sparta.logistics.common.auth.UserHeaders;
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

    public static final String SYSTEM = "SYSTEM";

    /**
     * Note: 요청 스레드에 묶인 현재 HTTP 요청(RequestContextHolder, 내부는 ThreadLocal)에서 X-Username을 꺼낸다.
     * 회원가입처럼 헤더가 없는 요청이나 스케줄러·기동 시 시드 적재처럼 요청 밖의 작업은 SYSTEM으로 기록한다(SA 5.1).
     */
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(attributes -> ((ServletRequestAttributes) attributes).getRequest().getHeader(UserHeaders.USERNAME))
                .filter(StringUtils::hasText)
                .or(() -> Optional.of(SYSTEM));
    }
}
