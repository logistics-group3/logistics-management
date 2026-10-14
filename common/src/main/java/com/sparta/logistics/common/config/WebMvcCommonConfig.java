package com.sparta.logistics.common.config;

import com.sparta.logistics.common.exception.GlobalExceptionHandler;
import com.sparta.logistics.common.security.CurrentUserArgumentResolver;
import com.sparta.logistics.common.security.RequireRoleInterceptor;
import com.sparta.logistics.common.security.UserContextInterceptor;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 사용자 컨텍스트, @RequireRole, @CurrentUser, 공통 예외 처리를 Servlet 서비스에 등록한다. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import(GlobalExceptionHandler.class)
class WebMvcCommonConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 순서 중요: 사용자 컨텍스트를 먼저 만든 뒤 역할을 확인한다.
        registry.addInterceptor(new UserContextInterceptor()).order(0);
        registry.addInterceptor(new RequireRoleInterceptor()).order(1);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver());
    }
}
