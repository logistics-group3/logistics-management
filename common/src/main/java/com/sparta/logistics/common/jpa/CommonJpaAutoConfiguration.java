package com.sparta.logistics.common.jpa;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 자동 설정. JPA 를 쓰는 서비스에서만 켜진다.
 * 주의: 서비스 쪽에 @EnableJpaAuditing 을 또 붙이면 빈이 중복되어 기동에 실패한다.
 */
@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration")
@ConditionalOnClass(name = {
        "jakarta.persistence.EntityManager",
        "org.springframework.data.jpa.domain.support.AuditingEntityListener"
})
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class CommonJpaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "auditorAware")
    public AuditorAware<String> auditorAware() {
        return new HeaderAuditorAware();
    }
}
