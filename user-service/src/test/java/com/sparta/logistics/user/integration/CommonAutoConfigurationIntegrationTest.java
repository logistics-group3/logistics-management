package com.sparta.logistics.user.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sparta.logistics.common.config.CommonWebMvcConfigurer;
import com.sparta.logistics.common.config.HeaderAuditorAware;
import com.sparta.logistics.common.exception.GlobalExceptionHandler;
import com.sparta.logistics.common.feign.FeignErrorDecoder;
import com.sparta.logistics.common.feign.FeignHeaderInterceptor;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

/**
 * 서비스는 common 패키지를 스캔하지 않는다. common의 공통 빈이 자동 설정만으로 한 번씩 등록되는지 실제 서비스 기동으로 확인한다.
 * 애너테이션을 UserAuthIntegrationTest와 같게 두어 Spring 컨텍스트를 재사용한다.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {"jwt.secret=test-only-jwt-secret-key-0123456789abcdef", "jwt.expiration-seconds=3600"})
class CommonAutoConfigurationIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("user-service 기동 시 common 공통 빈이 자동 설정으로 한 번씩 등록된다")
    void registersCommonBeansOnce() {
        assertThat(context.getBeansOfType(GlobalExceptionHandler.class)).hasSize(1);
        assertThat(context.getBeansOfType(CommonWebMvcConfigurer.class)).hasSize(1);
        assertThat(context.getBeansOfType(JPAQueryFactory.class)).hasSize(1);
        assertThat(context.getBean("auditorAware")).isInstanceOf(HeaderAuditorAware.class);
        assertThat(context.getBeansOfType(FeignHeaderInterceptor.class)).hasSize(1);
        assertThat(context.getBeansOfType(FeignErrorDecoder.class)).hasSize(1);
        assertThat(context.getBeansOfType(OpenAPI.class)).hasSize(1);
    }
}
