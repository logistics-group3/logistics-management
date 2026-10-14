package com.sparta.logistics.common.config;

import com.sparta.logistics.common.security.HeaderAuditorAware;
import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(EntityManager.class)
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
class JpaCommonConfig {

    @Bean
    @ConditionalOnMissingBean(name = "auditorAware")
    AuditorAware<String> auditorAware() {
        return new HeaderAuditorAware();
    }
}
