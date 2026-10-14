package com.sparta.logistics.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sparta.logistics.common.context.CurrentUserArgumentResolver;
import com.sparta.logistics.common.context.MasterOnlyInterceptor;
import com.sparta.logistics.common.exception.GlobalExceptionHandler;
import com.sparta.logistics.common.feign.FeignErrorDecoder;
import com.sparta.logistics.common.feign.FeignHeaderInterceptor;
import com.sparta.logistics.common.jpa.HeaderAuditorAware;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * common 모듈을 의존하는 모든 비즈니스 서비스에 자동 적용되는 설정.
 * (META-INF/spring/...AutoConfiguration.imports 로 등록되므로 서비스에서 별도 스캔이 필요 없다)
 */
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@EnableJpaAuditing(auditorAwareRef = "headerAuditorAware")
@Import(GlobalExceptionHandler.class)
public class CommonAutoConfiguration {

    @Bean
    public HeaderAuditorAware headerAuditorAware() {
        return new HeaderAuditorAware();
    }

    @Bean
    public WebMvcConfigurer userContextWebMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new CurrentUserArgumentResolver());
            }

            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new MasterOnlyInterceptor());
            }
        };
    }

    @Bean
    public JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
        return new JPAQueryFactory(entityManager);
    }

    @Bean
    public FeignHeaderInterceptor feignHeaderInterceptor() {
        return new FeignHeaderInterceptor();
    }

    @Bean
    public FeignErrorDecoder feignErrorDecoder(ObjectMapper objectMapper) {
        return new FeignErrorDecoder(objectMapper);
    }

    @Bean
    public OpenAPI openAPI(@Value("${spring.application.name}") String applicationName) {
        String scheme = "bearerAuth";
        return new OpenAPI()
                .info(new Info().title(applicationName + " API").version("v1"))
                // 상대 경로: Swagger UI를 연 곳(Gateway :8080) 기준으로 호출한다. 컨테이너 내부 주소 노출 방지.
                .servers(List.of(new Server().url("/")))
                .components(new Components().addSecuritySchemes(scheme,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(scheme));
    }
}
