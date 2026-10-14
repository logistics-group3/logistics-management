package com.sparta.logistics.common.config;

import com.sparta.logistics.common.auth.CurrentUserArgumentResolver;
import com.sparta.logistics.common.auth.RequireRoleInterceptor;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Note: Spring Data도 WebMvcConfigurer로 Pageable 처리기를 등록한다. 처리기 목록은 등록 순서대로 먼저 맞는 것이 쓰이므로,
 * 이 설정을 가장 먼저 실행해 공통 규칙(SearchPageableArgumentResolver)이 Pageable을 맡게 한다.
 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver());
        resolvers.add(new SearchPageableArgumentResolver());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequireRoleInterceptor());
    }
}
